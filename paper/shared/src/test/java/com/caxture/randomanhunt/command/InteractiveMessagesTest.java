package com.caxture.randomanhunt.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InteractiveMessagesTest {
    @Test
    void eachTeamButtonRunsTheCorrespondingPublicCommand() {
        var prompt = InteractiveMessages.teamPrompt(Component.text("Choose:"),
                Component.text("[Hunter]"), Component.text("[Runner]"), Component.text("[Random]"));
        var buttons = prompt.children().stream().filter(child -> child.clickEvent() != null).toList();
        assertEquals(3, buttons.size());
        assertEquals(ClickEvent.runCommand("/mhu join hunter"), buttons.get(0).clickEvent());
        assertEquals(ClickEvent.runCommand("/mhu join runner"), buttons.get(1).clickEvent());
        assertEquals(ClickEvent.runCommand("/mhu join random"), buttons.get(2).clickEvent());
    }

    @Test
    void numericConfigEntryPreservesTheTrailingSpaceAndHoversTheInternalName() {
        var line = InteractiveMessages.configEntry(Component.text("Speedrunner count: 2"), "runner-count",
                ClickEvent.suggestCommand("/mhu config runner-count "));
        assertEquals(ClickEvent.suggestCommand("/mhu config runner-count "), line.clickEvent());
        assertEquals(HoverEvent.showText(Component.text("runner-count")), line.hoverEvent());
    }
}
