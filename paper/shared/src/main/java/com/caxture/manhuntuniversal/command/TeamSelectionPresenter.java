package com.caxture.manhuntuniversal.command;

import com.caxture.manhuntuniversal.i18n.LanguageService;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.function.BooleanSupplier;

public final class TeamSelectionPresenter {
    private final LanguageService language;
    private final BooleanSupplier selectionEnabled;

    public TeamSelectionPresenter(LanguageService language, BooleanSupplier selectionEnabled) {
        this.language = language;
        this.selectionEnabled = selectionEnabled;
    }

    public void show(Player player) {
        if (!selectionEnabled.getAsBoolean()) return;
        player.sendMessage(InteractiveMessages.teamPrompt(
                language.chatComponent(player, "team.prompt", Map.of()),
                language.component(player, "team.button.hunter", Map.of()),
                language.component(player, "team.button.runner", Map.of()),
                language.component(player, "team.button.random", Map.of())));
    }
}
