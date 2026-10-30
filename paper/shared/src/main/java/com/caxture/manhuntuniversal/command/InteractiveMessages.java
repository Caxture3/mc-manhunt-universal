package com.caxture.manhuntuniversal.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class InteractiveMessages {
    private InteractiveMessages() {
    }

    public static Component teamPrompt(Component header, Component hunter, Component runner, Component random) {
        return header.append(Component.text("\n- "))
                .append(hunter.clickEvent(ClickEvent.runCommand("/mhu join hunter")))
                .append(Component.space())
                .append(runner.clickEvent(ClickEvent.runCommand("/mhu join runner")))
                .append(Component.space())
                .append(random.clickEvent(ClickEvent.runCommand("/mhu join random")));
    }

    public static Component configEntry(Component line, String internalName, ClickEvent action) {
        return line.hoverEvent(HoverEvent.showText(Component.text(internalName))).clickEvent(action);
    }

    public static List<Component> configMap(Component heading, String rootKey,
                                             Map<String, String> entries, boolean interactive) {
        List<Component> lines = new ArrayList<>();
        ClickEvent first = interactive && !entries.isEmpty()
                ? ClickEvent.suggestCommand("/mhu config " + rootKey + "." + entries.keySet().iterator().next() + " ")
                : null;
        lines.add(configEntry(heading, rootKey, first));
        int remaining = entries.size();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String key = rootKey + "." + entry.getKey();
            Component line = Component.text("    " + entry.getKey() + ": ", NamedTextColor.GRAY)
                    .append(Component.text(entry.getValue(), NamedTextColor.WHITE));
            if (--remaining > 0) line = line.append(Component.text(",", NamedTextColor.GRAY));
            lines.add(configEntry(line, key, interactive
                    ? ClickEvent.suggestCommand("/mhu config " + key + " ") : null));
        }
        lines.add(Component.text("}", NamedTextColor.GRAY));
        return List.copyOf(lines);
    }
}
