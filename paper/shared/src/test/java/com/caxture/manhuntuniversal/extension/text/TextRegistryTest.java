package com.caxture.manhuntuniversal.extension.text;
import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.text.*;
import com.caxture.manhuntuniversal.extension.OwnedRegistry;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TextRegistryTest {
    private final ExtensionRegistration owner = new ExtensionRegistration() {
        public String id() { return "test:text"; } public Set<ControlDomain> controls() { return Set.of(); }
        public boolean active() { return true; } public void close() { }
    };
    private final List<RuntimeException> errors = new ArrayList<>();
    private final OwnedRegistry owned = new OwnedRegistry(() -> {}, () -> null, value -> value == owner, errors::add);
    private final TextRegistryService registry = new TextRegistryService(owned);
    private TextContext context(String locale) { return new TextContext("prefix", locale, Optional.empty(), Map.of()); }
    @Test void localePriorityAndRemovalRevealTheCurrentUnderlyingProvider() {
        registry.override(owner, "prefix", 1, context -> "base");
        var high = registry.override(owner, "prefix", 2, context -> context.locale().startsWith("zh") ? "穹猎扩展" : "Extension").value().orElseThrow();
        assertEquals("穹猎扩展", registry.resolve(context("zh_cn")).orElseThrow());
        assertEquals("Extension", registry.resolve(context("en_us")).orElseThrow());
        high.close(); assertEquals("base", registry.resolve(context("en_us")).orElseThrow());
        owned.closeOwner(owner); assertTrue(registry.resolve(context("en_us")).isEmpty());
    }
    @Test void exceptionsFallThroughAndDefinitionsArePluginOwned() {
        registry.override(owner, "prefix", 2, context -> { throw new IllegalStateException("bad"); });
        assertTrue(registry.resolve(context("en_us")).isEmpty()); assertEquals(1, errors.size());
        var handle = registry.register(owner, "test:message", LocalizedText.bilingual("Hello {name}", "你好 {name}")).value().orElseThrow();
        var query = new TextContext("test:message", "zh_cn", Optional.empty(), Map.of("name", "Alex"));
        assertEquals("你好 {name}", registry.resolve(query).orElseThrow());
        handle.close(); assertTrue(registry.resolve(query).isEmpty());
    }
}
