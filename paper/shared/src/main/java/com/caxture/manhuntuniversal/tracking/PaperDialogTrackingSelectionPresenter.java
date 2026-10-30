package com.caxture.manhuntuniversal.tracking;

import com.caxture.manhuntuniversal.i18n.LanguageService;
import com.caxture.manhuntuniversal.common.CountdownText;
import com.caxture.manhuntuniversal.protocol.TrackingAvailability;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class PaperDialogTrackingSelectionPresenter implements TrackingSelectionPresenter {
    private static final List<TrackingTargetId> RUNNER_CHOICES = List.of(
            TrackingTargetId.VILLAGE,
            TrackingTargetId.NETHER_FORTRESS,
            TrackingTargetId.BASTION_REMNANT
    );

    private final LanguageService languages;
    private final Map<UUID, ActiveDialog> activeDialogs = new HashMap<>();

    public PaperDialogTrackingSelectionPresenter(LanguageService languages) {
        this.languages = languages;
    }

    @Override
    public void show(
            Player player,
            UUID sessionId,
            long authorizationId,
            TrackingTargetId currentSelection,
            TrackingAvailability availability,
            Consumer<TrackingTargetId> selectionHandler,
            Runnable dismiss
    ) {
        invalidate(player.getUniqueId());
        TrackingDialogWindow<DialogActions> window = new TrackingDialogWindow<>(
                currentSelection, availability, selectionHandler,
                () -> dismissActive(player, sessionId, authorizationId, dismiss));
        ActiveDialog active = new ActiveDialog(sessionId, authorizationId, window);
        activeDialogs.put(player.getUniqueId(), active);
        render(player, active);
    }

    @Override
    public void update(Player player, UUID sessionId, long authorizationId,
                       TrackingTargetId currentSelection, TrackingAvailability availability) {
        ActiveDialog active = activeDialogs.get(player.getUniqueId());
        if (active == null || !active.matches(sessionId, authorizationId)
                || !active.window().update(currentSelection, availability)) return;
        render(player, active);
    }

    @Override
    public void close(Player player, UUID sessionId, long authorizationId) {
        ActiveDialog active = activeDialogs.get(player.getUniqueId());
        if (active == null || !active.matches(sessionId, authorizationId)) return;
        activeDialogs.remove(player.getUniqueId());
        active.window().close();
        player.closeDialog();
    }

    @Override
    public void invalidate(UUID playerId) {
        ActiveDialog active = activeDialogs.remove(playerId);
        if (active != null) active.window().close();
    }

    private void dismissActive(Player player, UUID sessionId, long authorizationId, Runnable dismiss) {
        ActiveDialog expected = activeDialogs.get(player.getUniqueId());
        if (expected == null || !expected.matches(sessionId, authorizationId)) return;
        try {
            dismiss.run();
        } finally {
            // When used without the router, dismissal must still close after any previous refresh.
            if (activeDialogs.get(player.getUniqueId()) == expected) close(player, sessionId, authorizationId);
        }
    }

    private void render(Player player, ActiveDialog active) {
        TrackingDialogWindow<DialogActions> window = active.window();
        if (!window.availability().hasVisibleOptions()) {
            close(player, active.sessionId(), active.authorizationId());
            return;
        }
        DialogActions actions = window.actions(System.nanoTime(), revision -> actions(player, active, revision));
        List<ActionButton> buttons = RUNNER_CHOICES.stream()
                .filter(target -> window.availability().visible(TrackingSelectionTargetMapper.toProtocol(target)))
                .map(target -> button(player, target, window, actions))
                .toList();
        ActionButton exit = ActionButton.builder(Component.translatable("gui.cancel"))
                .width(150).action(actions.dismiss()).build();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(languages.component(player, "tracking.dialog.title", Map.of()))
                        .canCloseWithEscape(true)
                        // Native buttons with no action are still clickable. Keep the UI open for those no-ops;
                        // valid selection and exit callbacks explicitly send closeDialog through the owner.
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .pause(false)
                        .build())
                .type(DialogType.multiAction(buttons, exit, buttons.size())));
        // Replacing the displayed dialog does not invoke its exit action.
        player.showDialog(dialog);
    }

    private DialogActions actions(Player player, ActiveDialog active, long revision) {
        Map<TrackingTargetId, DialogAction> choices = new EnumMap<>(TrackingTargetId.class);
        for (TrackingTargetId target : RUNNER_CHOICES) {
            choices.put(target, callback(player, active,
                    () -> active.window().select(revision, target)));
        }
        return new DialogActions(choices, callback(player, active, () -> active.window().dismiss(revision)));
    }

    private DialogAction callback(Player player, ActiveDialog active, Runnable handler) {
        return DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player clickedPlayer
                            && clickedPlayer.getUniqueId().equals(player.getUniqueId())
                            && activeDialogs.get(player.getUniqueId()) == active) {
                        handler.run();
                    }
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES)
                        .lifetime(Duration.ofMinutes(5)).build());
    }

    private ActionButton button(
            Player player,
            TrackingTargetId target,
            TrackingDialogWindow<DialogActions> window,
            DialogActions actions
    ) {
        boolean selected = window.currentSelection() == target;
        long remaining = window.availability().remainingMillis(TrackingSelectionTargetMapper.toProtocol(target));
        Component label = languages.component(player,
                selected ? "tracking.dialog.button-selected" : "tracking.dialog.button",
                Map.of("target", languages.text(player, target.languageKey())));
        if (remaining > 0) label = label.append(Component.text(" | " + CountdownText.format(remaining)));
        return ActionButton.builder(label)
                .tooltip(remaining > 0
                        ? languages.component(player, "tracking.dialog.locked-tooltip", Map.of("time", CountdownText.format(remaining)))
                        : languages.component(player, "tracking.dialog.tooltip", Map.of()))
                .width(150)
                .action(remaining == 0 ? actions.choices().get(target) : null)
                .build();
    }

    private record DialogActions(Map<TrackingTargetId, DialogAction> choices, DialogAction dismiss) {}

    private record ActiveDialog(UUID sessionId, long authorizationId, TrackingDialogWindow<DialogActions> window) {
        boolean matches(UUID expectedSessionId, long expectedAuthorizationId) {
            return sessionId.equals(expectedSessionId) && authorizationId == expectedAuthorizationId;
        }
    }
}
