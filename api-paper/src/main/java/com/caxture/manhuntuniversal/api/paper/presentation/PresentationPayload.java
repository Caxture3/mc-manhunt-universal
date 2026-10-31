package com.caxture.manhuntuniversal.api.paper.presentation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import java.util.*;
/** Text uses one line, title uses title/subtitle, lore and result chat may contain multiple lines. */
public record PresentationPayload(List<Component> lines, Optional<PresentationSound> sound, Optional<Title.Times> titleTimes,
                                  Optional<io.papermc.paper.dialog.Dialog> dialog) {
    public PresentationPayload { lines = List.copyOf(lines); Objects.requireNonNull(sound); Objects.requireNonNull(titleTimes); Objects.requireNonNull(dialog); }
    public PresentationPayload(List<Component> lines, Optional<PresentationSound> sound, Optional<Title.Times> titleTimes) {
        this(lines, sound, titleTimes, Optional.empty());
    }
    public PresentationPayload withDialog(io.papermc.paper.dialog.Dialog replacement) { return new PresentationPayload(lines, sound, titleTimes, Optional.of(replacement)); }
    public static PresentationPayload text(Component text) { return new PresentationPayload(List.of(text), Optional.empty(), Optional.empty()); }
}
