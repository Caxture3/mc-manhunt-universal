package com.caxture.manhuntuniversal.fabric.network;

import com.caxture.manhuntuniversal.fabric.ui.CompassTrackingSelectionScreen;
import com.caxture.manhuntuniversal.protocol.ClientHello;
import com.caxture.manhuntuniversal.protocol.ProtocolCodec;
import com.caxture.manhuntuniversal.protocol.ProtocolConstants;
import com.caxture.manhuntuniversal.protocol.ProtocolMessage;
import com.caxture.manhuntuniversal.protocol.ServerHello;
import com.caxture.manhuntuniversal.protocol.TrackingSelectionChoose;
import com.caxture.manhuntuniversal.protocol.TrackingSelectionClose;
import com.caxture.manhuntuniversal.protocol.TrackingSelectionOpen;
import com.caxture.manhuntuniversal.protocol.TrackingSelectionTarget;
import com.caxture.manhuntuniversal.protocol.TrackingSelectionUpdate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.UUID;

/** Owns exact-version handshake retries and the authorized selector lifecycle. */
public final class ClientProtocolController {
    private static final int HANDSHAKE_RETRY_TICKS = 20;

    private final Minecraft minecraft;
    private final Logger logger;
    private boolean compatible;
    private boolean handshakeResolved;
    private int retryTicks;

    public ClientProtocolController(Minecraft minecraft, Logger logger) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void register() {
        PayloadTypeRegistry.serverboundPlay().register(MainChannelPayload.TYPE, MainChannelPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MainChannelPayload.TYPE, MainChannelPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(MainChannelPayload.TYPE,
                (payload, context) -> receive(payload));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            disconnect();
            trySendHello();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> disconnect());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!handshakeResolved && ++retryTicks >= HANDSHAKE_RETRY_TICKS) {
                retryTicks = 0;
                trySendHello();
            }
        });
    }

    private void trySendHello() {
        if (!ClientPlayNetworking.canSend(MainChannelPayload.TYPE)) {
            return;
        }
        ClientPlayNetworking.send(new MainChannelPayload(
                ProtocolCodec.encode(new ClientHello(ProtocolConstants.VERSION))));
    }

    private void receive(MainChannelPayload payload) {
        try {
            ProtocolMessage message = ProtocolCodec.decode(payload.data());
            var connection = minecraft.getConnection();
            minecraft.execute(() -> {
                if (connection != null && minecraft.getConnection() == connection) {
                    handleMessage(message);
                }
            });
        } catch (IllegalArgumentException exception) {
            logger.warn("Ignored malformed RandoManhunt payload", exception);
        }
    }

    private void handleMessage(ProtocolMessage message) {
        switch (message) {
            case ServerHello hello -> acceptServerHello(hello);
            case TrackingSelectionOpen open -> {
                if (compatible && open.protocolVersion() == ProtocolConstants.VERSION) {
                    openSelector(open);
                }
            }
            case TrackingSelectionClose close -> {
                if (compatible && close.protocolVersion() == ProtocolConstants.VERSION) {
                    closeSelector(close.sessionId(), close.authorizationId());
                }
            }
            case TrackingSelectionUpdate update -> {
                if (compatible && update.protocolVersion() == ProtocolConstants.VERSION
                        && minecraft.gui.screen() instanceof CompassTrackingSelectionScreen selector
                        && selector.matches(update.sessionId(), update.authorizationId())) {
                    selector.update(update);
                }
            }
            case ClientHello ignored -> logger.warn(
                    "Received an unexpected CLIENT_HELLO from the server");
            case TrackingSelectionChoose ignored -> logger.warn(
                    "Received an unexpected TRACKING_SELECTION_CHOOSE from the server");
        }
    }

    private void acceptServerHello(ServerHello hello) {
        handshakeResolved = true;
        compatible = hello.compatible()
                && hello.protocolVersion() == ProtocolConstants.VERSION;
        if (!compatible) {
            closeAnySelector();
            logger.warn("RandoManhunt protocol mismatch: client={}, server={}",
                    ProtocolConstants.VERSION, hello.protocolVersion());
        }
    }

    private void disconnect() {
        compatible = false;
        handshakeResolved = false;
        retryTicks = 0;
        closeAnySelector();
    }

    private void openSelector(TrackingSelectionOpen request) {
        if (!request.availability().hasVisibleOptions()) {
            closeSelector(request.sessionId(), request.authorizationId());
            return;
        }
        minecraft.setScreenAndShow(new CompassTrackingSelectionScreen(request,
                selected -> sendSelection(request, selected), () -> sendCancellation(request)));
    }

    private void sendCancellation(TrackingSelectionOpen request) {
        if (compatible && ClientPlayNetworking.canSend(MainChannelPayload.TYPE)) {
            ClientPlayNetworking.send(new MainChannelPayload(ProtocolCodec.encode(
                    new TrackingSelectionClose(ProtocolConstants.VERSION,
                            request.sessionId(), request.authorizationId()))));
        }
    }

    private void sendSelection(TrackingSelectionOpen request,
                               TrackingSelectionTarget selected) {
        if (!compatible || !ClientPlayNetworking.canSend(MainChannelPayload.TYPE)) {
            return;
        }
        ClientPlayNetworking.send(new MainChannelPayload(ProtocolCodec.encode(
                new TrackingSelectionChoose(
                        ProtocolConstants.VERSION,
                        request.sessionId(),
                        request.authorizationId(),
                        selected))));
    }

    private void closeSelector(UUID sessionId, long authorizationId) {
        if (minecraft.gui.screen() instanceof CompassTrackingSelectionScreen selector
                && selector.matches(sessionId, authorizationId)) {
            selector.closeFromServer();
        }
    }

    private void closeAnySelector() {
        if (minecraft.gui.screen() instanceof CompassTrackingSelectionScreen selector) {
            minecraft.execute(() -> {
                if (minecraft.gui.screen() == selector) {
                    selector.closeFromServer();
                }
            });
        }
    }
}
