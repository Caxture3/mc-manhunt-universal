package com.caxture.manhuntuniversal.protocol;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolCodecTest {
    private static final UUID SESSION = new UUID(0x1234, 0x5678);

    @Test
    void helloRetainsItsWireShapeAndExposesVersionMismatchToNegotiation() {
        assertArrayEquals(new byte[]{1, 0, 0, 0, 2},
                ProtocolCodec.encode(new ClientHello(ProtocolConstants.VERSION)));
        assertArrayEquals(new byte[]{2, 0, 0, 0, 2, 0},
                ProtocolCodec.encode(new ServerHello(ProtocolConstants.VERSION, false)));
        assertEquals(new ClientHello(1), ProtocolCodec.decode(new byte[]{1, 0, 0, 0, 1}));
        assertEquals(new ServerHello(1, false),
                ProtocolCodec.decode(new byte[]{2, 0, 0, 0, 1, 0}));
    }

    @Test
    void allMessageTypesRoundTripWithoutLosingSelectionAvailability() {
        var availability = new TrackingAvailability(-1, 0, Long.MAX_VALUE);
        List<ProtocolMessage> messages = List.of(
                new ClientHello(2), new ServerHello(2, true),
                new TrackingSelectionOpen(2, SESSION, 7, TrackingSelectionTarget.NETHER_FORTRESS,
                        availability),
                new TrackingSelectionUpdate(2, SESSION, 7, TrackingSelectionTarget.NONE,
                        availability),
                new TrackingSelectionChoose(2, SESSION, 7, TrackingSelectionTarget.BASTION_REMNANT),
                new TrackingSelectionClose(2, SESSION, 7));
        for (var message : messages) {
            assertEquals(message, ProtocolCodec.decode(ProtocolCodec.encode(message)));
        }
    }

    @Test
    void availabilityIsAppendedAfterTheExistingSelectionFieldsForOpenAndTypeSixUpdate() {
        var state = new TrackingAvailability(123, -1, 456);
        for (var message : List.of(
                new TrackingSelectionOpen(2, SESSION, 7, TrackingSelectionTarget.VILLAGE, state),
                new TrackingSelectionUpdate(2, SESSION, 7, TrackingSelectionTarget.VILLAGE, state))) {
            var expected = ByteBuffer.allocate(54)
                    .put((byte) (message instanceof TrackingSelectionOpen ? 3 : 6))
                    .putInt(2).putLong(0x1234).putLong(0x5678).putLong(7).put((byte) 1)
                    .putLong(123).putLong(-1).putLong(456).array();
            assertArrayEquals(expected, ProtocolCodec.encode(message));
        }
    }

    @Test
    void oldOpenConstructorDefaultsToAllAvailableStructures() {
        var message = new TrackingSelectionOpen(2, SESSION, 7, TrackingSelectionTarget.NONE);
        assertEquals(new TrackingAvailability(0, 0, 0), message.availability());
    }

    @Test
    void rejectsEveryTruncatedMessageAndEveryTrailingByte() {
        for (var message : List.of(
                new ClientHello(2), new ServerHello(2, true),
                new TrackingSelectionOpen(2, SESSION, 7, TrackingSelectionTarget.NONE),
                new TrackingSelectionUpdate(2, SESSION, 7, TrackingSelectionTarget.NONE,
                        TrackingAvailability.allUnlocked()),
                new TrackingSelectionChoose(2, SESSION, 7, TrackingSelectionTarget.VILLAGE),
                new TrackingSelectionClose(2, SESSION, 7))) {
            byte[] encoded = ProtocolCodec.encode(message);
            for (int length = 0; length < encoded.length; length++) {
                byte[] truncated = Arrays.copyOf(encoded, length);
                assertThrows(ProtocolException.class, () -> ProtocolCodec.decode(truncated));
            }
            assertThrows(ProtocolException.class,
                    () -> ProtocolCodec.decode(Arrays.copyOf(encoded, encoded.length + 1)));
        }
    }

    @Test
    void rejectsInvalidAvailabilityAtEachWirePositionInBothStateMessages() {
        for (var message : List.of(
                new TrackingSelectionOpen(2, SESSION, 7, TrackingSelectionTarget.NONE),
                new TrackingSelectionUpdate(2, SESSION, 7, TrackingSelectionTarget.NONE,
                        TrackingAvailability.allUnlocked()))) {
            for (int offset : new int[]{30, 38, 46}) {
                byte[] invalid = ProtocolCodec.encode(message);
                ByteBuffer.wrap(invalid).putLong(offset, -2);
                assertThrows(ProtocolException.class, () -> ProtocolCodec.decode(invalid));
            }
        }
    }

    @Test
    void rejectsInvalidSelectionAndAuthorizationAndPayloadBounds() {
        byte[] invalidSelection = ProtocolCodec.encode(
                new TrackingSelectionOpen(2, SESSION, 7, TrackingSelectionTarget.NONE));
        invalidSelection[29] = 4;
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode(invalidSelection));
        byte[] invalidAuthorization = ProtocolCodec.encode(
                new TrackingSelectionUpdate(2, SESSION, 7, TrackingSelectionTarget.NONE,
                        TrackingAvailability.allUnlocked()));
        ByteBuffer.wrap(invalidAuthorization).putLong(21, 0);
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode(invalidAuthorization));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode(null));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode(new byte[1025]));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode(new byte[]{127}));
    }
}
