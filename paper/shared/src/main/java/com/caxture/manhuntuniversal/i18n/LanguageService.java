package com.caxture.manhuntuniversal.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class LanguageService {
    private final JavaPlugin plugin;
    private final BooleanSupplier showChatPrefix;
    private final Map<SupportedLanguage, YamlConfiguration> builtIn =
            new EnumMap<>(SupportedLanguage.class);
    private final Map<SupportedLanguage, YamlConfiguration> external =
            new EnumMap<>(SupportedLanguage.class);
    private final Set<String> reportedMissingKeys = new HashSet<>();
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();
    private com.caxture.manhuntuniversal.extension.text.TextRegistryService textRegistry;
    private com.caxture.manhuntuniversal.extension.presentation.PresentationDispatcher presentation;

    public void extensions(com.caxture.manhuntuniversal.extension.text.TextRegistryService texts,
                           com.caxture.manhuntuniversal.extension.presentation.PresentationDispatcher display) {
        textRegistry = texts; presentation = display;
    }

    public LanguageService(JavaPlugin plugin, BooleanSupplier showChatPrefix) {
        this.plugin = plugin;
        this.showChatPrefix = showChatPrefix;
        for (SupportedLanguage language : SupportedLanguage.values()) {
            String resourcePath = resourcePath(language);
            File externalFile = new File(plugin.getDataFolder(), resourcePath);
            if (!externalFile.exists()) {
                plugin.saveResource(resourcePath, false);
            }
            builtIn.put(language, loadBuiltIn(resourcePath));
            external.put(language, YamlConfiguration.loadConfiguration(externalFile));
        }
    }

    public void send(CommandSender recipient, String key) {
        send(recipient, key, Map.of());
    }

    public void send(CommandSender recipient, String key, Map<String, String> placeholders) {
        sendComponent(recipient, "core:text." + key, render(recipient, key, placeholders, showChatPrefix.getAsBoolean()));
    }

    public void sendComponent(CommandSender recipient, String id, Component component) {
        if (presentation == null) recipient.sendMessage(component); else presentation.chat(recipient, id, component, false);
    }
    public void actionbar(Player recipient, String id, Component component) {
        if (presentation == null) recipient.sendActionBar(component); else presentation.actionbar(recipient, id, component);
    }
    public void title(Player recipient, String id, net.kyori.adventure.title.Title title) {
        if (presentation == null) recipient.showTitle(title); else presentation.title(recipient, id, title);
    }
    public void sound(Player recipient, String id, String key, float volume, float pitch) {
        if (presentation == null) recipient.playSound(recipient.getLocation(), key, volume, pitch); else presentation.sound(recipient, id, key, volume, pitch);
    }
    public java.util.List<Component> item(Player recipient, String id, boolean lore, java.util.List<Component> lines) {
        if (presentation == null) return lines;
        var result = presentation.transform(recipient, id, lore
                        ? com.caxture.manhuntuniversal.api.paper.presentation.PresentationChannel.ITEM_LORE
                        : com.caxture.manhuntuniversal.api.paper.presentation.PresentationChannel.ITEM_NAME,
                new com.caxture.manhuntuniversal.api.paper.presentation.PresentationPayload(lines, java.util.Optional.empty(), java.util.Optional.empty()), false);
        return result.cancelled() ? java.util.List.of() : result.payload().lines();
    }
    public java.util.Optional<com.caxture.manhuntuniversal.api.MatchSnapshot> currentMatch() {
        return presentation == null ? java.util.Optional.empty() : presentation.currentMatch();
    }
    public void withResult(com.caxture.manhuntuniversal.api.ResultSnapshot result, Runnable action) {
        if (presentation == null) action.run(); else presentation.withResult(result, action);
    }
    public boolean allowMenu(Player player, String id, boolean fabric) {
        if (presentation == null) return true;
        return !presentation.transform(player, id, fabric
                        ? com.caxture.manhuntuniversal.api.paper.presentation.PresentationChannel.FABRIC_MENU
                        : com.caxture.manhuntuniversal.api.paper.presentation.PresentationChannel.PAPER_MENU,
                com.caxture.manhuntuniversal.api.paper.presentation.PresentationPayload.text(component(player, "tracking.dialog.title", Map.of())), false).cancelled();
    }
    public void showDialog(Player player, String id, io.papermc.paper.dialog.Dialog dialog) {
        if (presentation == null) { player.showDialog(dialog); return; }
        var result = presentation.transform(player, id, com.caxture.manhuntuniversal.api.paper.presentation.PresentationChannel.PAPER_MENU,
                com.caxture.manhuntuniversal.api.paper.presentation.PresentationPayload.text(component(player, "tracking.dialog.title", Map.of())).withDialog(dialog), false);
        if (!result.cancelled()) player.showDialog(result.payload().dialog().orElse(dialog));
    }

    public Component component(CommandSender recipient, String key, Map<String, String> placeholders) {
        return render(recipient, key, placeholders, false);
    }

    public Component chatComponent(CommandSender recipient, String key, Map<String, String> placeholders) {
        return render(recipient, key, placeholders, showChatPrefix.getAsBoolean());
    }

    public String text(CommandSender recipient, String key) {
        return text(recipient, key, Map.of());
    }

    public com.caxture.manhuntuniversal.api.LocalizedText localized(String key) {
        return com.caxture.manhuntuniversal.api.LocalizedText.bilingual(resolve(SupportedLanguage.EN_US, key), resolve(SupportedLanguage.ZH_CN, key));
    }

    public String text(CommandSender recipient, String key, Map<String, String> placeholders) {
        String message = template(recipient, key, placeholders);
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            message = message.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
        }
        return message;
    }

    private Component render(
            CommandSender recipient,
            String key,
            Map<String, String> placeholders,
            boolean includePrefix
    ) {
        SupportedLanguage language = languageOf(recipient);
        String message = template(recipient, key, placeholders);
        if (!"prefix".equals(key)) {
            message = PrefixExpander.expand(message, template(recipient, "prefix", Map.of()), includePrefix);
        }
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            message = message.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
        }
        Component fallback = serializer.deserialize(message);
        return textRegistry == null ? fallback : textRegistry.component(new com.caxture.manhuntuniversal.api.paper.text.PaperTextContext(
                textContext(recipient, key, placeholders), recipient, includePrefix, fallback));
    }

    private com.caxture.manhuntuniversal.api.text.TextContext textContext(CommandSender recipient, String key, Map<String, String> placeholders) {
        return new com.caxture.manhuntuniversal.api.text.TextContext(key, languageOf(recipient).resourceName(),
                recipient instanceof Player player ? java.util.Optional.of(player.getUniqueId()) : java.util.Optional.empty(), placeholders);
    }
    private String template(CommandSender recipient, String key, Map<String, String> placeholders) {
        if(textRegistry==null)return resolve(languageOf(recipient),key);
        return textRegistry.resolveOverride(textContext(recipient,key,placeholders)).orElseGet(()->textRegistry.definition(key)
                .map(definition->LanguageTemplateResolver.extension(external.get(languageOf(recipient)),key,definition.fallback(),definition.resolve(languageOf(recipient).resourceName())))
                .orElseGet(()->resolve(languageOf(recipient),key)));
    }
    public String localizedValue(CommandSender recipient,String key,com.caxture.manhuntuniversal.api.LocalizedText fallback) {
        String normal=LanguageTemplateResolver.extension(external.get(languageOf(recipient)),key,fallback.fallback(),fallback.resolve(languageOf(recipient).resourceName()));
        return textRegistry==null?normal:textRegistry.resolveOverride(textContext(recipient,key,Map.of())).orElse(normal);
    }

    private SupportedLanguage languageOf(CommandSender recipient) {
        return recipient instanceof Player player
                ? LocaleSelector.select(player.locale())
                : SupportedLanguage.ZH_CN;
    }

    private String resolve(SupportedLanguage language, String key) {
        return LanguageTemplateResolver.resolve(
                external.get(language),
                builtIn.get(language),
                builtIn.get(SupportedLanguage.EN_US),
                key,
                this::reportLanguageIssue);
    }

    private void reportLanguageIssue(String key) {
        if (reportedMissingKeys.add(key)) {
            plugin.getLogger().warning("Missing or malformed language value for key: " + key);
        }
    }

    private YamlConfiguration loadBuiltIn(String resourcePath) {
        InputStream stream = plugin.getResource(resourcePath);
        if (stream == null) {
            throw new IllegalStateException("Missing built-in language resource: " + resourcePath);
        }
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load built-in language resource: "
                    + resourcePath, exception);
        }
    }

    private static String resourcePath(SupportedLanguage language) {
        return "lang/" + language.resourceName() + ".yml";
    }
}
