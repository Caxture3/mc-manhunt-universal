package com.caxture.manhuntuniversal.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

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
}
