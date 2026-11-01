package com.caxture.manhuntuniversal.tracking;

import com.caxture.manhuntuniversal.api.tracking.*;
import com.caxture.manhuntuniversal.client.*;
import com.caxture.manhuntuniversal.common.CountdownText;
import com.caxture.manhuntuniversal.i18n.LanguageService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.*;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import java.time.Duration;
import java.util.*;
import java.util.function.*;

/** One session-authorized menu model shared by Fabric and native Dialog. */
final class DynamicTrackingSelectionPresenter {
    interface Choice { boolean choose(Player player, UUID session, long authorization, long revision, String target); }
    private final TrackingClientPublisher clients;
    private final LanguageService languages;
    private final Choice choice;
    private final Predicate<Player> eligible;
    private final Map<UUID, Window> windows = new HashMap<>();
    private final TrackingSelectionAuthorizationRegistry authorizations = new TrackingSelectionAuthorizationRegistry();
    DynamicTrackingSelectionPresenter(TrackingClientPublisher clients, LanguageService languages, Predicate<Player> eligible, Choice choice) {
        this.clients = clients; this.languages = languages; this.eligible = eligible; this.choice = choice;
    }
    void open(Player player, UUID session, TrackingMenuSnapshot menu) {
        close(player); if (menu.options().isEmpty()) return;
        var authorization = authorizations.issue(player.getUniqueId(), session);
        Window window = new Window(session, authorization.authorizationId(), menu); windows.put(player.getUniqueId(), window);
        window.fabric = clients.supportsCompatibleClient(player.getUniqueId()) && languages.allowMenu(player, "core:tracking.menu", true)
                && DynamicTrackingMessages.tryOpen(session,window.authorization,menu).map(message->clients.openDynamic(player,message)).orElse(false);
        if (!window.fabric) render(player, window);
    }
    void update(Player player, TrackingMenuSnapshot menu) {
        Window window = windows.get(player.getUniqueId()); if (window == null) return;
        if (!eligible.test(player) || menu.options().isEmpty()) { close(player); return; }
        if (window.menu.equals(menu) && (window.fabric ? clients.supportsCompatibleClient(player.getUniqueId())
                : !DynamicMenuActionPolicy.renew(window.renderedAt,System.nanoTime()))) return;
        window.menu = menu; window.renderVersion++;
        if (window.fabric && DynamicTrackingMessages.tryUpdate(window.session,window.authorization,menu).map(message->clients.updateDynamic(player,message)).orElse(false)) return;
        if (window.fabric) clients.closeTrackingSelection(player, window.session, window.authorization);
        window.fabric = false; render(player, window);
    }
    boolean choose(Player player, UUID session, long authorization, long revision, String target) {
        Window window = windows.get(player.getUniqueId());
        if (window == null || !window.session.equals(session) || window.authorization != authorization
                || window.menu.revision() != revision || !eligible.test(player)) return false;
        if (!choice.choose(player, session, authorization, revision, target)) return false;
        close(player); return true;
    }
    void cancel(Player player, UUID session, long authorization) {
        Window window = windows.get(player.getUniqueId());
        if (window != null && window.session.equals(session) && window.authorization == authorization) close(player);
    }
    void close(Player player) {
        Window window = windows.remove(player.getUniqueId()); authorizations.invalidate(player.getUniqueId());
        if (window == null) return;
        if (window.fabric) clients.closeTrackingSelection(player, window.session, window.authorization); else player.closeDialog();
    }
    void forget(UUID player) { windows.remove(player); authorizations.invalidate(player); }
    void clear(org.bukkit.Server server) {
        for (UUID id : List.copyOf(windows.keySet())) { Player player = server.getPlayer(id); if (player != null) close(player); else forget(id); }
    }
    private void render(Player player, Window window) {
        long rendered = ++window.renderVersion;
        long directory=window.menu.revision();window.renderedAt=System.nanoTime();
        List<ActionButton> buttons = window.menu.options().stream().map(option -> {
            String text = (window.menu.selection().filter(option.id()::equals).isPresent() ? "✓ " : "") + option.name();
            if (option.availability().remainingMillis() > 0) text += " | " + CountdownText.format(option.availability().remainingMillis());
            DialogAction action = option.availability().selectable() ? callback(player, window, rendered,
                    () -> choose(player, window.session, window.authorization, directory, option.id()),false,directory) : null;
            return ActionButton.builder(Component.text(text)).tooltip(Component.text(option.availability().selectable()
                    ? option.description() : option.availability().reason())).width(150).action(action).build();
        }).toList();
        var exit = ActionButton.builder(Component.translatable("gui.cancel")).width(150)
                .action(callback(player, window, rendered, () -> close(player),true,directory)).build();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(languages.component(player, "tracking.dialog.title", Map.of())).canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.NONE).pause(false).build())
                .type(DialogType.multiAction(buttons, exit, Math.max(1, Math.min(4, buttons.size())))));
        languages.showDialog(player, "core:tracking.menu", dialog);
    }
    private DialogAction callback(Player player, Window window, long rendered, Runnable action,boolean dismiss,long directory) {
        return DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player clicked && clicked.getUniqueId().equals(player.getUniqueId())
                    && DynamicMenuActionPolicy.valid(windows.get(player.getUniqueId())==window,directory,window.menu.revision(),dismiss)) action.run();
        }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).lifetime(Duration.ofMinutes(5)).build());
    }
    private static final class Window {
        final UUID session; final long authorization; TrackingMenuSnapshot menu; boolean fabric; long renderVersion,renderedAt;
        Window(UUID session, long authorization, TrackingMenuSnapshot menu) { this.session = session; this.authorization = authorization; this.menu = menu; }
    }
}
