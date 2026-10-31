package com.caxture.manhuntuniversal.extension.presentation;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.paper.presentation.*;
import com.caxture.manhuntuniversal.extension.OwnedRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.function.*;

/** Resolves data only; mandatory state cleanup is never passed through this renderer. */
public final class PresentationRegistryService implements PresentationRegistry {
    private final OwnedRegistry owned;
    private final BooleanSupplier hidden;
    private final List<Interceptor> interceptors = new ArrayList<>();
    private final List<Section> sections = new ArrayList<>();
    private final Map<UUID, Set<RefreshScope>> refreshes = new LinkedHashMap<>();
    private final List<IdentityEntry> identities = new ArrayList<>();
    private Runnable identityChanged = () -> {};
    public PresentationRegistryService(OwnedRegistry owned, BooleanSupplier hidden) { this.owned = owned; this.hidden = hidden; }
    public void identityChanged(Runnable action) { owned.requireThread(); identityChanged = action; }
    public boolean hasIdentityProviders() { owned.requireThread(); return !identities.isEmpty()
            || interceptors.stream().anyMatch(entry -> entry.selector.channels().contains(PresentationChannel.TAB) || entry.selector.channels().contains(PresentationChannel.NAMETAG)); }
    @Override public ExtensionResult<ExtensionHandle> identity(ExtensionRegistration owner, int priority, boolean reveal, ObserverIdentityProvider provider) {
        return owned.execute(owner, () -> {
            if (provider == null) return invalid();
            if (reveal && !owner.controls().contains(ControlDomain.ROLE_VISIBILITY)) return conflict("ROLE_VISIBILITY ownership required");
            if (identities.stream().anyMatch(entry -> entry.priority == priority)) return conflict("Same-priority identity provider");
            IdentityEntry entry = new IdentityEntry(owner, priority, reveal, provider); identities.add(entry);
            owned.callback(identityChanged);
            return ExtensionResult.ok(owned.add(owner, () -> { identities.remove(entry); owned.callback(identityChanged); }));
        });
    }
    public ObserverIdentityStyle identity(ObserverIdentityContext context) {
        owned.requireThread();
        IdentityEntry entry = identities.stream().filter(value->owned.valid(value.owner)).max(Comparator.comparingInt(value -> value.priority)).orElse(null);
        ObserverIdentityStyle style = context.fallback();
        if (entry != null) try {
            var filtered = new ObserverIdentityContext(context.observer(), context.targetId(), context.realName(),
                    hidden.getAsBoolean() && !entry.reveal ? Optional.empty() : context.match(), context.fallback());
            ObserverIdentityStyle replacement = owned.invoke(() -> entry.provider.present(filtered));
            if (replacement != null) style = replacement;
        } catch (RuntimeException | LinkageError error) { report(entry.owner, error); }
        var tab = process(new PresentationRequest("core:identity.tab." + context.targetId(), PresentationChannel.TAB, context.observer(),
                PresentationPayload.text(style.tabName()), context.match(), Optional.empty(), true,true));
        var name = process(new PresentationRequest("core:identity.nametag." + context.targetId(), PresentationChannel.NAMETAG, context.observer(),
                new PresentationPayload(List.of(style.namePrefix(), style.nameSuffix()), Optional.empty(), Optional.empty()), context.match(), Optional.empty(), true,true));
        return new ObserverIdentityStyle(tab.payload().lines().isEmpty() ? net.kyori.adventure.text.Component.empty() : tab.payload().lines().getFirst(),
                style.tabVisible() && !tab.cancelled(), style.tabOrder(),
                name.payload().lines().isEmpty() ? net.kyori.adventure.text.Component.empty() : name.payload().lines().getFirst(),
                name.payload().lines().size() < 2 ? net.kyori.adventure.text.Component.empty() : name.payload().lines().get(1), style.nameColor(),
                name.cancelled() ? Optional.of(false) : style.nameVisibility());
    }
    @Override public ExtensionResult<ExtensionHandle> intercept(ExtensionRegistration owner, PresentationSelector selector, int priority, PresentationProvider provider) {
        return owned.execute(owner, () -> {
            if (selector == null || provider == null) return invalid();
            if (selector.revealRoles() && !owner.controls().contains(ControlDomain.ROLE_VISIBILITY)) return conflict("ROLE_VISIBILITY ownership required");
            if (interceptors.stream().anyMatch(entry -> entry.priority == priority && overlap(entry.selector, selector))) return conflict("Overlapping same-priority renderer");
            Interceptor entry = new Interceptor(owner, selector, priority, provider); interceptors.add(entry);
            if (selector.channels().contains(PresentationChannel.TAB) || selector.channels().contains(PresentationChannel.NAMETAG)) owned.callback(identityChanged);
            return ExtensionResult.ok(owned.add(owner, () -> { interceptors.remove(entry); owned.callback(identityChanged); }));
        });
    }
    @Override public ExtensionResult<ExtensionHandle> appendResult(ExtensionRegistration owner, ResultSection section) {
        return owned.execute(owner, () -> {
            if (section == null || !section.id().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return invalid();
            if (section.revealRoles() && !owner.controls().contains(ControlDomain.ROLE_VISIBILITY)) return conflict("ROLE_VISIBILITY ownership required");
            if (sections.stream().anyMatch(entry -> entry.section.id().equals(section.id()))) return conflict("Duplicate result section ID");
            Section entry = new Section(owner, section); sections.add(entry);
            return ExtensionResult.ok(owned.add(owner, () -> sections.remove(entry)));
        });
    }
    @Override public ExtensionResult<Void> refresh(ExtensionRegistration owner, RefreshRequest request) {
        return owned.execute(owner, () -> {
            if (request == null) return invalid();
            refreshes.computeIfAbsent(request.playerId(), ignored -> EnumSet.noneOf(RefreshScope.class)).addAll(request.scopes());
            return ExtensionResult.ok(null);
        });
    }
    public void flushRefresh(Consumer<RefreshRequest> consumer) {
        owned.requireThread(); Map<UUID, Set<RefreshScope>> pending = new LinkedHashMap<>(refreshes); refreshes.clear();
        pending.forEach((id, scopes) -> consumer.accept(new RefreshRequest(id, scopes)));
    }
    public PresentationDecision process(PresentationRequest request) {
        owned.requireThread();
        PresentationPayload payload = request.payload();
        if (request.roleSensitive() && !request.privateToRecipient() && hidden.getAsBoolean()) payload = new PresentationPayload(List.of(), Optional.empty(), Optional.empty());
        if (request.result().isPresent() && request.channel() == PresentationChannel.CHAT) {
            List<Component> lines = new ArrayList<>();
            List<Section> sorted = sections.stream().filter(entry->owned.valid(entry.owner)).sorted(Comparator.comparingInt((Section entry) -> entry.section.order())
                    .thenComparing(entry -> entry.owner.id()).thenComparing(entry -> entry.section.id())).toList();
            for (Section entry : sorted) if (entry.section.order() < 0) appendSection(entry, request, lines);
            lines.addAll(payload.lines());
            for (Section entry : sorted) if (entry.section.order() >= 0) appendSection(entry, request, lines);
            payload = new PresentationPayload(lines, payload.sound(), payload.titleTimes());
        }
        Interceptor entry = interceptors.stream().filter(value -> owned.valid(value.owner) && matches(value.selector, request))
                .max(Comparator.comparingInt(value -> value.priority)).orElse(null);
        if (entry != null) try {
            PresentationPayload current = payload;
            PresentationDecision decision = owned.invoke(() -> entry.provider.present(filtered(request, current, entry.selector.revealRoles())));
            if (decision != null) return decision;
        } catch (RuntimeException | LinkageError error) { report(entry.owner, error); }
        return new PresentationDecision(request.roleSensitive() && !request.privateToRecipient() && hidden.getAsBoolean() && payload.lines().isEmpty(), payload);
    }
    private PresentationPayload payloadOf(PresentationRequest request, List<Component> lines) {
        return new PresentationPayload(lines, request.payload().sound(), request.payload().titleTimes());
    }
    private void appendSection(Section entry, PresentationRequest request, List<Component> lines) {
        try {
            List<Component> result = owned.invoke(() -> entry.section.content().apply(filtered(request, payloadOf(request, lines), entry.section.revealRoles())));
            if (result != null) lines.addAll(List.copyOf(result));
        } catch (RuntimeException | LinkageError error) { report(entry.owner, error); }
    }
    private PresentationRequest filtered(PresentationRequest request, PresentationPayload payload, boolean reveal) {
        boolean redact = hidden.getAsBoolean() && !reveal;
        if(redact&&request.roleSensitive())payload=new PresentationPayload(List.of(),Optional.empty(),payload.titleTimes());
        return new PresentationRequest(request.id(), request.channel(), request.recipient(), payload,
                redact ? Optional.empty() : request.match(), redact ? request.result().map(ResultSnapshot::redacted) : request.result(), request.roleSensitive(),request.privateToRecipient());
    }
    private static boolean matches(PresentationSelector selector, PresentationRequest request) {
        return selector.channels().contains(request.channel()) && request.id().startsWith(selector.idPrefix())
                && (selector.recipientId().isEmpty() || request.recipient() instanceof Player player && selector.recipientId().get().equals(player.getUniqueId()));
    }
    private static boolean overlap(PresentationSelector first, PresentationSelector second) {
        return first.channels().stream().anyMatch(second.channels()::contains)
                && (first.idPrefix().startsWith(second.idPrefix()) || second.idPrefix().startsWith(first.idPrefix()))
                && (first.recipientId().isEmpty() || second.recipientId().isEmpty() || first.recipientId().equals(second.recipientId()));
    }
    private void report(ExtensionRegistration owner, Throwable error) { owned.report(new IllegalStateException("Presentation provider failed: " + owner.id(), error)); }
    private static <T> ExtensionResult<T> invalid() { return ExtensionResult.rejected(OperationStatus.INVALID_ARGUMENT, "Invalid presentation registration"); }
    private static <T> ExtensionResult<T> conflict(String message) { return ExtensionResult.rejected(OperationStatus.CONTROL_CONFLICT, message); }
    private record Interceptor(ExtensionRegistration owner, PresentationSelector selector, int priority, PresentationProvider provider) { }
    private record Section(ExtensionRegistration owner, ResultSection section) { }
    private record IdentityEntry(ExtensionRegistration owner, int priority, boolean reveal, ObserverIdentityProvider provider) { }
}
