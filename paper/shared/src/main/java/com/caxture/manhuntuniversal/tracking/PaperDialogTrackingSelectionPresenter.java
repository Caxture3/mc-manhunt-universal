package com.caxture.manhuntuniversal.tracking;

import com.caxture.manhuntuniversal.i18n.LanguageService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.time.Duration;
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

    public PaperDialogTrackingSelectionPresenter(LanguageService languages) {
        this.languages = languages;
    }

    @Override
    public void show(
            Player player,
            UUID sessionId,
            long authorizationId,
            TrackingTargetId currentSelection,
            Consumer<TrackingTargetId> selectionHandler
    ) {
        List<ActionButton> buttons = RUNNER_CHOICES.stream()
                .map(target -> button(player, target, currentSelection == target, selectionHandler))
                .toList();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(languages.component(
                                player, "tracking.dialog.title", Map.of()))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .build())
                .type(DialogType.multiAction(buttons, null, 3)));
        player.showDialog(dialog);
    }

    @Override
    public void close(Player player, UUID sessionId, long authorizationId) {
        player.closeDialog();
    }

    @Override
    public void invalidate(UUID playerId) {
        // This presenter owns no state; the Fabric-aware router owns authorization routing.
    }

    private ActionButton button(
            Player player,
            TrackingTargetId target,
            boolean selected,
            Consumer<TrackingTargetId> selectionHandler
    ) {
        Component label = languages.component(player,
                selected ? "tracking.dialog.button-selected" : "tracking.dialog.button",
                Map.of("target", languages.text(player, target.languageKey())));
        return ActionButton.builder(label)
                .tooltip(languages.component(player, "tracking.dialog.tooltip", Map.of()))
                .width(150)
                .action(DialogAction.customClick(
                        (response, audience) -> {
                            if (audience instanceof Player clickedPlayer
                                    && clickedPlayer.getUniqueId().equals(player.getUniqueId())) {
                                selectionHandler.accept(selected ? TrackingTargetId.NONE : target);
                            }
                        },
                        ClickCallback.Options.builder()
                                .uses(1)
                                .lifetime(Duration.ofMinutes(5))
                                .build()))
                .build();
    }
}
