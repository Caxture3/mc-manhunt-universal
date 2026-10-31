package com.caxture.manhuntuniversal.api.paper.presentation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.*;
/** Empty nameVisibility preserves the observer's original scoreboard rule. The real player name is never replaced. */
public record ObserverIdentityStyle(Component tabName, boolean tabVisible, int tabOrder, Component namePrefix,
                                    Component nameSuffix, NamedTextColor nameColor, Optional<Boolean> nameVisibility) {
    public ObserverIdentityStyle { Objects.requireNonNull(tabName); Objects.requireNonNull(namePrefix); Objects.requireNonNull(nameSuffix);
        Objects.requireNonNull(nameColor); Objects.requireNonNull(nameVisibility); if (tabOrder < 0) throw new IllegalArgumentException("Negative Tab order"); }
    public ObserverIdentityStyle(Component tabName, boolean tabVisible, int tabOrder, Component prefix, Component suffix, NamedTextColor color, boolean visible) {
        this(tabName, tabVisible, tabOrder, prefix, suffix, color, Optional.of(visible));
    }
    public boolean nameVisible() { return nameVisibility.orElse(true); }
}
