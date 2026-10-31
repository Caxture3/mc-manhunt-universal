package com.caxture.manhuntuniversal.extension.presentation;
import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.paper.presentation.*;
import com.caxture.manhuntuniversal.extension.OwnedRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PresentationRegistryTest {
    private final ExtensionRegistration owner = new ExtensionRegistration() {
        public String id() { return "test:display"; } public Set<ControlDomain> controls() { return Set.of(); }
        public boolean active() { return true; } public void close() { }
    };
    private final OwnedRegistry owned = new OwnedRegistry(() -> {}, () -> null, token -> token == owner, error -> fail(error));
    private boolean hidden;
    private final PresentationRegistryService registry = new PresentationRegistryService(owned, () -> hidden);
    private final CommandSender recipient = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
            new Class<?>[]{CommandSender.class}, (proxy, method, args) -> "console");
    private PresentationRequest request() {
        UUID id = UUID.randomUUID();
        var result = new ResultSnapshot(id, MatchWinner.RUNNERS, "goal", Optional.empty(), Instant.EPOCH,
                Map.of(id, new ParticipantSnapshot(id, "Alex", TeamRole.RUNNER, false)), Optional.of(id));
        return new PresentationRequest("core:result.chat", PresentationChannel.CHAT, recipient,
                PresentationPayload.text(Component.text("original")), Optional.empty(), Optional.of(result), false);
    }
    @Test void normalProvidersGetRedactedResultAndCanReplaceOrCancelWithoutChangingActualResult() {
        hidden = true; var original = request();
        registry.intercept(owner, PresentationSelector.all(), 1, context -> {
            assertTrue(context.result().orElseThrow().participants().isEmpty());
            assertTrue(context.result().orElseThrow().killer().isEmpty());
            return PresentationDecision.replace(PresentationPayload.text(Component.text("replacement")));
        });
        assertEquals(Component.text("replacement"), registry.process(original).payload().lines().getFirst());
        assertEquals(1, original.result().orElseThrow().participants().size());
        var high = registry.intercept(owner, PresentationSelector.all(), 2, context -> PresentationDecision.cancel()).value().orElseThrow();
        assertTrue(registry.process(original).cancelled()); high.close();
        assertFalse(registry.process(original).cancelled());
    }
    @Test void sectionsComposeInOrderAndRefreshRequestsCoalesceWithoutInvokingLocators() {
        registry.appendResult(owner, new ResultSection("test:second", 2, false, context -> List.of(Component.text("second"))));
        registry.appendResult(owner, new ResultSection("test:first", 1, false, context -> List.of(Component.text("first"))));
        assertEquals(List.of(Component.text("original"), Component.text("first"), Component.text("second")), registry.process(request()).payload().lines());
        UUID player = UUID.randomUUID();
        registry.refresh(owner, new RefreshRequest(player, Set.of(RefreshScope.TEXT)));
        registry.refresh(owner, new RefreshRequest(player, Set.of(RefreshScope.TEXT, RefreshScope.ITEMS)));
        List<RefreshRequest> flushed = new ArrayList<>(); registry.flushRefresh(flushed::add);
        assertEquals(1, flushed.size()); assertEquals(Set.of(RefreshScope.TEXT, RefreshScope.ITEMS), flushed.getFirst().scopes());
    }
    @Test void visibilityTakeoverRequiresAnExplicitControlDomainAndCleanupIsNotARequest() {
        var reveal = new PresentationSelector(Set.of(PresentationChannel.CHAT), "", Optional.empty(), true);
        assertEquals(OperationStatus.CONTROL_CONFLICT, registry.intercept(owner, reveal, 1, context -> PresentationDecision.cancel()).status());
    }
    @Test void identityProviderUsesTheObserverAndRemovalRestoresCurrentFallback() {
        hidden = true; UUID observerId = UUID.randomUUID(), target = UUID.randomUUID();
        org.bukkit.entity.Player observer = (org.bukkit.entity.Player) Proxy.newProxyInstance(
                org.bukkit.entity.Player.class.getClassLoader(), new Class<?>[]{org.bukkit.entity.Player.class},
                (proxy, method, args) -> method.getName().equals("getUniqueId") ? observerId : null);
        var fallback = new ObserverIdentityStyle(Component.text("Alex"), true, 0, Component.empty(), Component.empty(),
                net.kyori.adventure.text.format.NamedTextColor.WHITE, Optional.empty());
        var context = new ObserverIdentityContext(observer, target, "Alex", Optional.empty(), fallback);
        var handle = registry.identity(owner, 1, false, input -> {
            assertEquals(observerId, input.observer().getUniqueId()); assertEquals("Alex", input.realName());
            assertTrue(input.match().isEmpty());
            return new ObserverIdentityStyle(Component.text("Private"), false, 7, Component.text("[X]"), Component.empty(),
                    net.kyori.adventure.text.format.NamedTextColor.AQUA, false);
        }).value().orElseThrow();
        assertFalse(registry.identity(context).tabVisible()); assertFalse(registry.identity(context).nameVisible());
        handle.close(); assertEquals(fallback, registry.identity(context));
    }
    @Test void privateIdentityOutputIsKeptForRecipientButRedactedBeforeOrdinaryRenderers() {
        hidden=true;
        registry.intercept(owner,PresentationSelector.all(),1,context->{assertTrue(context.payload().lines().isEmpty());return null;});
        var payload=new PresentationPayload(List.of(Component.text("role"),Component.text("RUNNER")),Optional.empty(),Optional.empty());
        var request=new PresentationRequest("core:role.title",PresentationChannel.TITLE,recipient,payload,Optional.empty(),Optional.empty(),true,true);
        assertEquals(payload,registry.process(request).payload());assertFalse(registry.process(request).cancelled());
    }
}
