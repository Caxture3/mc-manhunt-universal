package com.caxture.manhuntuniversal.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.stream.Collectors;

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

    @Test
    void mappedConfigUsesBracedRowsWithIndependentSuggestionsAndTrailingSpaces() {
        var entries = new LinkedHashMap<String, String>();
        entries.put("village", "300");
        entries.put("bastion_remnant", "900");
        entries.put("nether_fortress", "0");
        String root = "runner-structure-tracker-unlock-time";
        var heading = Component.text("- 速通者结构追踪解锁时间（秒） = {");
        var rows = InteractiveMessages.configMap(heading, root, entries, true);
        var plain = PlainTextComponentSerializer.plainText();
        assertEquals("- 速通者结构追踪解锁时间（秒） = {\n"
                + "    village: 300,\n    bastion_remnant: 900,\n    nether_fortress: 0\n}",
                rows.stream().map(plain::serialize).collect(Collectors.joining("\n")));
        int row = 1;
        for (String child : entries.keySet()) {
            String key = root + "." + child;
            assertEquals(ClickEvent.suggestCommand("/mhu config " + key + " "), rows.get(row).clickEvent());
            assertEquals(HoverEvent.showText(Component.text(key)), rows.get(row).hoverEvent());
            row++;
        }
        assertEquals(rows.get(1).clickEvent(), rows.getFirst().clickEvent());
        assertNull(rows.getLast().clickEvent());
        var console = InteractiveMessages.configMap(heading, root, entries, false);
        assertTrue(console.stream().allMatch(line -> line.clickEvent() == null));
        assertEquals(rows.stream().map(plain::serialize).toList(), console.stream().map(plain::serialize).toList());
    }
}
