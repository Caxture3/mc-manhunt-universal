package com.caxture.manhuntuniversal.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.UUID;

public final class ProtocolCodec {
    private static final int CLIENT_HELLO = 1;
    private static final int SERVER_HELLO = 2;
    private static final int TRACKING_SELECTION_OPEN = 3;
    private static final int TRACKING_SELECTION_CHOOSE = 4;
    private static final int TRACKING_SELECTION_CLOSE = 5;
    private static final int TRACKING_SELECTION_UPDATE = 6;
    private static final int DYNAMIC_TRACKING_OPEN = 7;
    private static final int DYNAMIC_TRACKING_UPDATE = 8;
    private static final int DYNAMIC_TRACKING_CHOOSE = 9;

    private ProtocolCodec() {
    }

    public static byte[] encode(ProtocolMessage message) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                switch (message) {
                    case DynamicTrackingOpen open -> {
                        output.writeByte(DYNAMIC_TRACKING_OPEN);
                        DynamicTrackingWire.writeCatalog(output, open.protocolVersion(), open.sessionId(), open.authorizationId(), open.directoryRevision(), open.currentSelection(), open.targets());
                    }
                    case DynamicTrackingUpdate update -> {
                        output.writeByte(DYNAMIC_TRACKING_UPDATE);
                        DynamicTrackingWire.writeCatalog(output, update.protocolVersion(), update.sessionId(), update.authorizationId(), update.directoryRevision(), update.currentSelection(), update.targets());
                    }
                    case DynamicTrackingChoose choose -> {
                        output.writeByte(DYNAMIC_TRACKING_CHOOSE);
                        DynamicTrackingWire.writeHeader(output, choose.protocolVersion(), choose.sessionId(), choose.authorizationId(), choose.directoryRevision());
                        DynamicTrackingWire.string(output, choose.targetId());
                    }
                    case ClientHello hello -> {
                        output.writeByte(CLIENT_HELLO);
                        output.writeInt(hello.protocolVersion());
                    }
                    case ServerHello hello -> {
                        output.writeByte(SERVER_HELLO);
                        output.writeInt(hello.protocolVersion());
                        output.writeBoolean(hello.compatible());
                    }
                    case TrackingSelectionOpen open -> {
                        output.writeByte(TRACKING_SELECTION_OPEN);
                        output.writeInt(open.protocolVersion());
                        writeUuid(output, open.sessionId());
                        output.writeLong(open.authorizationId());
                        output.writeByte(open.currentSelection().wireId());
                        writeAvailability(output, open.availability());
                    }
                    case TrackingSelectionUpdate update -> {
                        output.writeByte(TRACKING_SELECTION_UPDATE);
                        output.writeInt(update.protocolVersion());
                        writeUuid(output, update.sessionId());
                        output.writeLong(update.authorizationId());
                        output.writeByte(update.currentSelection().wireId());
                        writeAvailability(output, update.availability());
                    }
                    case TrackingSelectionChoose choose -> {
                        output.writeByte(TRACKING_SELECTION_CHOOSE);
                        output.writeInt(choose.protocolVersion());
                        writeUuid(output, choose.sessionId());
                        output.writeLong(choose.authorizationId());
                        output.writeByte(choose.selection().wireId());
                    }
                    case TrackingSelectionClose close -> {
                        output.writeByte(TRACKING_SELECTION_CLOSE);
                        output.writeInt(close.protocolVersion());
                        writeUuid(output, close.sessionId());
                        output.writeLong(close.authorizationId());
                    }
                }
            }
            byte[] result = bytes.toByteArray();
            if (result.length > ProtocolConstants.MAX_PAYLOAD_BYTES) {
                throw new ProtocolException("Encoded payload is too large");
            }
            return result;
        } catch (IOException exception) {
            throw new ProtocolException("Unable to encode protocol message", exception);
        }
    }

    public static ProtocolMessage decode(byte[] payload) {
        if (payload == null || payload.length == 0
                || payload.length > ProtocolConstants.MAX_PAYLOAD_BYTES) {
            throw new ProtocolException("Invalid payload length");
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(payload))) {
            ProtocolMessage message = switch (input.readUnsignedByte()) {
                case DYNAMIC_TRACKING_OPEN -> DynamicTrackingWire.readOpen(input);
                case DYNAMIC_TRACKING_UPDATE -> DynamicTrackingWire.readUpdate(input);
                case DYNAMIC_TRACKING_CHOOSE -> DynamicTrackingWire.readChoose(input);
                case CLIENT_HELLO -> new ClientHello(input.readInt());
                case SERVER_HELLO -> new ServerHello(
                        input.readInt(), input.readBoolean());
                case TRACKING_SELECTION_OPEN -> new TrackingSelectionOpen(
                        input.readInt(), readUuid(input), input.readLong(),
                        TrackingSelectionTarget.fromWireId(input.readUnsignedByte()),
                        readAvailability(input));
                case TRACKING_SELECTION_UPDATE -> new TrackingSelectionUpdate(
                        input.readInt(), readUuid(input), input.readLong(),
                        TrackingSelectionTarget.fromWireId(input.readUnsignedByte()),
                        readAvailability(input));
                case TRACKING_SELECTION_CHOOSE -> new TrackingSelectionChoose(
                        input.readInt(), readUuid(input), input.readLong(),
                        TrackingSelectionTarget.fromWireId(input.readUnsignedByte()));
                case TRACKING_SELECTION_CLOSE -> new TrackingSelectionClose(
                        input.readInt(), readUuid(input), input.readLong());
                default -> throw new ProtocolException("Unknown message type");
            };
            if (input.available() != 0) {
                throw new ProtocolException("Protocol payload has trailing bytes");
            }
            return message;
        } catch (EOFException exception) {
            throw new ProtocolException("Truncated protocol payload", exception);
        } catch (IOException exception) {
            throw new ProtocolException("Unable to decode protocol message", exception);
        } catch (IllegalArgumentException exception) {
            if (exception instanceof ProtocolException protocolException) {
                throw protocolException;
            }
            throw new ProtocolException("Invalid protocol message", exception);
        }
    }

    static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static void writeAvailability(DataOutputStream output, TrackingAvailability value)
            throws IOException {
        output.writeLong(value.villageMillis());
        output.writeLong(value.netherFortressMillis());
        output.writeLong(value.bastionRemnantMillis());
    }

    private static TrackingAvailability readAvailability(DataInputStream input) throws IOException {
        return new TrackingAvailability(input.readLong(), input.readLong(), input.readLong());
    }

    static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }
}
