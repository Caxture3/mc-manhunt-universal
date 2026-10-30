package com.caxture.manhuntuniversal.client;

import com.caxture.manhuntuniversal.protocol.ClientHello;
import com.caxture.manhuntuniversal.protocol.ProtocolCodec;
import com.caxture.manhuntuniversal.protocol.ProtocolConstants;
import com.caxture.manhuntuniversal.protocol.ProtocolException;
import com.caxture.manhuntuniversal.protocol.ProtocolMessage;
import com.caxture.manhuntuniversal.protocol.ServerHello;
import com.caxture.manhuntuniversal.protocol.TrackingSelectionChoose;
import com.caxture.manhuntuniversal.protocol.TrackingSelectionClose;
import com.caxture.manhuntuniversal.protocol.TrackingSelectionOpen;
import com.caxture.manhuntuniversal.protocol.TrackingSelectionTarget;
import com.caxture.manhuntuniversal.protocol.TrackingSelectionUpdate;
import com.caxture.manhuntuniversal.protocol.TrackingAvailability;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Paper plugin-channel bridge for the optional Fabric selector client. */
public final class PaperTrackingClientPublisher
        implements TrackingClientPublisher, PluginMessageListener, Listener {
    private final JavaPlugin plugin;
    private final ClientCapabilityRegistry capabilities = new ClientCapabilityRegistry();
    private BiConsumer<Player, TrackingSelectionChoose> selectionHandler =
            (ignoredPlayer, ignoredChoice) -> { };
    private BiConsumer<Player, TrackingSelectionClose> closeHandler =
            (ignoredPlayer, ignoredClose) -> { };

    public PaperTrackingClientPublisher(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(
                plugin, ProtocolConstants.CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(
                plugin, ProtocolConstants.CHANNEL, this);
    }

    public void setSelectionHandler(
            BiConsumer<Player, TrackingSelectionChoose> selectionHandler) {
        this.selectionHandler = Objects.requireNonNull(selectionHandler, "selectionHandler");
    }

    public void setCloseHandler(BiConsumer<Player, TrackingSelectionClose> closeHandler) {
        this.closeHandler = Objects.requireNonNull(closeHandler, "closeHandler");
    }

    @Override
    public boolean supportsCompatibleClient(UUID playerId) {
        return capabilities.supports(playerId);
    }

    @Override
    public void openTrackingSelection(
            Player player,
            UUID sessionId,
            long authorizationId,
            TrackingSelectionTarget currentSelection,
            TrackingAvailability availability
    ) {
        if (capabilities.supports(player.getUniqueId())) {
            send(player, new TrackingSelectionOpen(
                    ProtocolConstants.VERSION,
                    sessionId,
                    authorizationId,
                    currentSelection,
                    availability));
        }
    }

    @Override
    public void updateTrackingSelection(Player player, UUID sessionId, long authorizationId,
                                        TrackingSelectionTarget currentSelection,
                                        TrackingAvailability availability) {
        if (capabilities.supports(player.getUniqueId())) {
            send(player, new TrackingSelectionUpdate(ProtocolConstants.VERSION,
                    sessionId, authorizationId, currentSelection, availability));
        }
    }

    @Override
    public void closeTrackingSelection(
            Player player,
            UUID sessionId,
            long authorizationId
    ) {
        if (capabilities.supports(player.getUniqueId())) {
            send(player, new TrackingSelectionClose(
                    ProtocolConstants.VERSION, sessionId, authorizationId));
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!ProtocolConstants.CHANNEL.equals(channel)) {
            return;
        }
        try {
            ProtocolMessage decoded = ProtocolCodec.decode(message);
            if (decoded instanceof ClientHello hello) {
                boolean compatible = capabilities.accept(player.getUniqueId(), hello);
                send(player, new ServerHello(ProtocolConstants.VERSION, compatible));
            } else if (decoded instanceof TrackingSelectionChoose choice
                    && capabilities.supports(player.getUniqueId())
                    && choice.protocolVersion() == ProtocolConstants.VERSION) {
                selectionHandler.accept(player, choice);
            } else if (decoded instanceof TrackingSelectionClose close
                    && capabilities.supports(player.getUniqueId())
                    && close.protocolVersion() == ProtocolConstants.VERSION) {
                closeHandler.accept(player, close);
            } else {
                plugin.getLogger().warning(
                        "Ignoring unexpected client message from " + player.getName());
            }
        } catch (ProtocolException exception) {
            capabilities.disconnect(player.getUniqueId());
            plugin.getLogger().warning("Ignoring malformed client message from "
                    + player.getName() + ": " + exception.getMessage());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        capabilities.disconnect(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        capabilities.clear();
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(
                plugin, ProtocolConstants.CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(
                plugin, ProtocolConstants.CHANNEL);
    }

    private void send(Player player, ProtocolMessage message) {
        player.sendPluginMessage(
                plugin, ProtocolConstants.CHANNEL, ProtocolCodec.encode(message));
    }
}
