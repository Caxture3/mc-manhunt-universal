package com.caxture.manhuntuniversal.command;

public final class CommandAccessPolicy {
    private CommandAccessPolicy() {
    }

    public static boolean adminAllowed(boolean hasPermission) {
        return hasPermission;
    }

}
