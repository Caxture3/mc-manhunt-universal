package com.caxture.manhuntuniversal.api.paper.text;
import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.text.TextRegistry;
import net.kyori.adventure.text.Component;
import java.util.function.Function;
/** Rich overrides affect component rendering; plain text lookups use TextProvider templates. */
public interface PaperTextRegistry extends TextRegistry {
    ExtensionResult<ExtensionHandle> component(ExtensionRegistration owner, String key, int priority, Function<PaperTextContext, Component> provider);
}
