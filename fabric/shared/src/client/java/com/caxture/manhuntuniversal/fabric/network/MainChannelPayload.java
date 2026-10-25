package com.caxture.manhuntuniversal.fabric.network;

import com.caxture.manhuntuniversal.protocol.ProtocolConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Arrays;

/** Raw plugin-channel payload. The protocol module owns the bytes inside it. */
public record MainChannelPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<MainChannelPayload> TYPE = new Type<>(
            Identifier.parse(ProtocolConstants.CHANNEL));
    public static final StreamCodec<RegistryFriendlyByteBuf, MainChannelPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBytes(payload.data),
            buffer -> {
                int length = buffer.readableBytes();
                if (length <= 0 || length > ProtocolConstants.MAX_PAYLOAD_BYTES) {
                    throw new IllegalArgumentException("Invalid ManhuntUniversal payload length: " + length);
                }
                byte[] data = new byte[length];
                buffer.readBytes(data);
                return new MainChannelPayload(data);
            });

    public MainChannelPayload {
        if (data == null || data.length == 0 || data.length > ProtocolConstants.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Invalid payload data");
        }
        data = Arrays.copyOf(data, data.length);
    }

    @Override
    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }

    @Override
    public Type<MainChannelPayload> type() {
        return TYPE;
    }
}
