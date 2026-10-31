package com.caxture.manhuntuniversal.protocol;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
final class DynamicTrackingWire {
    private DynamicTrackingWire() { }
    static void writeCatalog(DataOutputStream out, int version, UUID session, long authorization, long revision,
                             Optional<String> selected, List<TrackingTargetDescriptor> targets) throws IOException {
        writeHeader(out, version, session, authorization, revision); optional(out, selected); out.writeInt(targets.size());
        for (var target : targets) {
            string(out, target.id()); string(out, target.name()); string(out, target.secondaryName()); string(out, target.description());
            out.writeInt(target.order()); string(out, target.theme()); out.writeInt(target.color()); optional(out, target.texture()); optional(out, target.itemIcon());
            out.writeLong(target.remainingMillis()); out.writeBoolean(target.selectable()); string(out, target.lockedReason());
        }
    }
    static void writeHeader(DataOutputStream out, int version, UUID session, long authorization, long revision) throws IOException {
        out.writeInt(version); ProtocolCodec.writeUuid(out, session); out.writeLong(authorization); out.writeLong(revision);
    }
    static DynamicTrackingOpen readOpen(DataInputStream in) throws IOException {
        Header header = header(in); Optional<String> selected = optional(in, 128);
        return new DynamicTrackingOpen(header.version, header.session, header.authorization, header.revision, selected, targets(in));
    }
    static DynamicTrackingUpdate readUpdate(DataInputStream in) throws IOException {
        Header header = header(in); Optional<String> selected = optional(in, 128);
        return new DynamicTrackingUpdate(header.version, header.session, header.authorization, header.revision, selected, targets(in));
    }
    static DynamicTrackingChoose readChoose(DataInputStream in) throws IOException {
        Header header = header(in); return new DynamicTrackingChoose(header.version, header.session, header.authorization, header.revision, string(in, 128));
    }
    private static Header header(DataInputStream in) throws IOException {
        return new Header(in.readInt(), ProtocolCodec.readUuid(in), in.readLong(), in.readLong());
    }
    private static List<TrackingTargetDescriptor> targets(DataInputStream in) throws IOException {
        int count = in.readInt(); if (count < 0 || count > 32) throw new ProtocolException("Invalid target count");
        List<TrackingTargetDescriptor> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) result.add(new TrackingTargetDescriptor(string(in, 128), string(in, 64), string(in, 64), string(in, 128),
                in.readInt(), string(in, 32), in.readInt(), optional(in, 256), optional(in, 256), in.readLong(), in.readBoolean(), string(in, 128)));
        return result;
    }
    static void string(DataOutputStream out, String value) throws IOException {
        ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
        byte[] bytes = new byte[encoded.remaining()]; encoded.get(bytes); out.writeInt(bytes.length); out.write(bytes);
    }
    private static String string(DataInputStream in, int maximumPoints) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > maximumPoints * 4 || length > in.available()) throw new ProtocolException("Invalid string byte length");
        String value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(in.readNBytes(length))).toString();
        DynamicTrackingValidation.text(value, maximumPoints, false); return value;
    }
    private static void optional(DataOutputStream out, Optional<String> value) throws IOException {
        out.writeBoolean(value.isPresent()); if (value.isPresent()) string(out, value.get());
    }
    private static Optional<String> optional(DataInputStream in, int maximumPoints) throws IOException { return in.readBoolean() ? Optional.of(string(in, maximumPoints)) : Optional.empty(); }
    private record Header(int version, UUID session, long authorization, long revision) { }
}
