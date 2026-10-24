package com.caxture.randomanhunt.command;

import com.caxture.randomanhunt.i18n.LanguageService;
import org.bukkit.entity.Player;

import java.util.Map;

public final class TeamSelectionPresenter {
    private final LanguageService language;

    public TeamSelectionPresenter(LanguageService language) {
        this.language = language;
    }

    public void show(Player player) {
        player.sendMessage(InteractiveMessages.teamPrompt(
                language.chatComponent(player, "team.prompt", Map.of()),
                language.component(player, "team.button.hunter", Map.of()),
                language.component(player, "team.button.runner", Map.of()),
                language.component(player, "team.button.random", Map.of())));
    }
}
