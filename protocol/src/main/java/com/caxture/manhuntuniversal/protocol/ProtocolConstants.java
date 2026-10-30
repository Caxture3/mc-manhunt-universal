package com.caxture.manhuntuniversal.protocol;

public final class ProtocolConstants {
    public static final int VERSION = 2;
    public static final String CHANNEL = ProtocolNamespace.NAMESPACE + ":main";
    public static final int MAX_PAYLOAD_BYTES = 1024;

    private ProtocolConstants() {
    }
}
