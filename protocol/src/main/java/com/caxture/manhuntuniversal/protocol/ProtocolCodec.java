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

    private ProtocolCodec() {
    }

    public static byte[] encode(ProtocolMessage message) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                switch (message) {
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
                case CLIENT_HELLO -> new ClientHello(input.readInt());
                case SERVER_HELLO -> new ServerHello(
                        input.readInt(), input.readBoolean());
                case TRACKING_SELECTION_OPEN -> new TrackingSelectionOpen(
                        input.readInt(), readUuid(input), input.readLong(),
                        TrackingSelectionTarget.fromWireId(input.readUnsignedByte()));
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

    private static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }
}
