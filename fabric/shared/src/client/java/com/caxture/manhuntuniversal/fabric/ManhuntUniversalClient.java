package com.caxture.manhuntuniversal.fabric;

import com.caxture.manhuntuniversal.fabric.network.ClientProtocolController;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ManhuntUniversalClient implements ClientModInitializer {
    public static final String MOD_ID = "randomanhunt";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        new ClientProtocolController(Minecraft.getInstance(), LOGGER).register();
    }
}
