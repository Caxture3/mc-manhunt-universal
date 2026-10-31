package com.caxture.manhuntuniversal.extension.presentation;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.paper.presentation.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.function.Supplier;

/** Output boundary. Restoring state/closing old interfaces bypasses this optional output path. */
public final class PresentationDispatcher {
    private final PresentationRegistryService registry;
    private final Supplier<Optional<MatchSnapshot>> match;
    private ResultSnapshot result;
    public PresentationDispatcher(PresentationRegistryService registry, Supplier<Optional<MatchSnapshot>> match) {
        this.registry = registry; this.match = match;
    }
    public Optional<MatchSnapshot> currentMatch() { return match.get(); }
    public void withResult(ResultSnapshot snapshot, Runnable action) {
        ResultSnapshot previous = result; result = snapshot;
        try { action.run(); } finally { result = previous; }
    }
    public PresentationDecision transform(CommandSender receiver, String id, PresentationChannel channel, PresentationPayload payload, boolean sensitive) {
        return registry.process(new PresentationRequest(id, channel, receiver, payload, match.get(), Optional.ofNullable(result), sensitive));
    }
    public void chat(CommandSender receiver, String id, Component text, boolean sensitive) {
        PresentationDecision output = transform(receiver, result == null ? id : "core:result.chat", PresentationChannel.CHAT, PresentationPayload.text(text), sensitive);
        if (!output.cancelled()) output.payload().lines().forEach(receiver::sendMessage);
    }
    public void actionbar(Player receiver, String id, Component text) {
        PresentationDecision output = transform(receiver, id, PresentationChannel.ACTIONBAR, PresentationPayload.text(text), false);
        if (!output.cancelled()) receiver.sendActionBar(join(output.payload().lines()));
    }
    public void title(Player receiver, String id, Title title) {
        boolean identity=id.equals("core:role.title");
        PresentationDecision output = registry.process(new PresentationRequest(id,PresentationChannel.TITLE,receiver,
                new PresentationPayload(List.of(title.title(), title.subtitle()), Optional.empty(), Optional.ofNullable(title.times())),
                match.get(),Optional.ofNullable(result),identity,identity));
        if (!output.cancelled()) {
            List<Component> lines = output.payload().lines();
            receiver.showTitle(Title.title(lines.isEmpty() ? Component.empty() : lines.getFirst(),
                    lines.size() < 2 ? Component.empty() : lines.get(1), output.payload().titleTimes().orElse(Title.DEFAULT_TIMES)));
        }
    }
    public void sound(Player receiver, String id, String key, float volume, float pitch) {
        PresentationDecision output = transform(receiver, id, PresentationChannel.SOUND,
                new PresentationPayload(List.of(), Optional.of(new PresentationSound(key, volume, pitch)), Optional.empty()), false);
        if (!output.cancelled()) output.payload().sound().ifPresent(sound -> receiver.playSound(receiver.getLocation(), sound.key(), sound.volume(), sound.pitch()));
    }
    private static Component join(List<Component> lines) {
        Component result = Component.empty();
        for (int index = 0; index < lines.size(); index++) result = result.append(index == 0 ? Component.empty() : Component.space()).append(lines.get(index));
        return result;
    }
}
