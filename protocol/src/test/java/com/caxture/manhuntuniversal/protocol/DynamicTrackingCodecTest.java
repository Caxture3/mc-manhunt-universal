package com.caxture.manhuntuniversal.protocol;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;
class DynamicTrackingCodecTest {
    private final UUID session = UUID.randomUUID();
    private TrackingTargetDescriptor target(String id) {
        return new TrackingTargetDescriptor(id, "检查点", "CHECKPOINT", "Finish the task", 5, "generic", 0xff303030,
                Optional.of("example:textures/checkpoint.png"), Optional.of("minecraft:compass"), 1500, false, "Wait");
    }
    @Test void dynamicCatalogAndChoiceRoundTripWithoutChangingHelloBytes() {
        var options = List.of(target("test:checkpoint"));
        for (ProtocolMessage message : List.of(new DynamicTrackingOpen(3, session, 1, 9, Optional.empty(), options),
                new DynamicTrackingUpdate(3, session, 1, 10, Optional.of("test:checkpoint"), options),
                new DynamicTrackingChoose(3, session, 1, 10, "test:checkpoint"))) {
            assertEquals(message, ProtocolCodec.decode(ProtocolCodec.encode(message)));
        }
        assertArrayEquals(new byte[]{1,0,0,0,2}, ProtocolCodec.encode(new ClientHello(2)));
        assertArrayEquals(new byte[]{2,0,0,0,3,0}, ProtocolCodec.encode(new ServerHello(3, false)));
        assertEquals(3, ProtocolConstants.VERSION);
    }
    @Test void duplicateIdsOversizedLabelsAndInvalidResourcesAreRejected() {
        var option = target("test:checkpoint");
        assertThrows(IllegalArgumentException.class, () -> new DynamicTrackingOpen(3, session, 1, 0, Optional.empty(), List.of(option, option)));
        assertThrows(IllegalArgumentException.class, () -> new DynamicTrackingOpen(3, session, 1, 0, Optional.empty(),
                java.util.stream.IntStream.range(0, 33).mapToObj(index -> target("test:t" + index)).toList()));
        assertThrows(IllegalArgumentException.class, () -> target("https://example.org/image.png"));
        String emoji = "😀".repeat(64);
        assertDoesNotThrow(() -> new TrackingTargetDescriptor("test:t", emoji, "", "", 0, "generic", 0,
                Optional.empty(), Optional.empty(), 0, true, ""));
        assertThrows(IllegalArgumentException.class, () -> new TrackingTargetDescriptor("test:t", emoji + "x", "", "", 0, "generic", 0,
                Optional.empty(), Optional.empty(), 0, true, ""));
    }
    @Test void truncatedMaliciousLengthsAndMalformedUtf8AreRejectedBeforeAllocatingCatalogs() {
        byte[] data = ProtocolCodec.encode(new DynamicTrackingChoose(3, session, 1, 0, "test:x"));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode(Arrays.copyOf(data, data.length - 1)));
        ByteBuffer.wrap(data).putInt(37, Integer.MAX_VALUE);
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode(data));
        byte[] malformed = ProtocolCodec.encode(new DynamicTrackingChoose(3, session, 1, 0, "test:x"));
        malformed[41] = (byte)0xc0; malformed[42] = (byte)0xaf;
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode(malformed));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode(new byte[65537]));
    }
}
