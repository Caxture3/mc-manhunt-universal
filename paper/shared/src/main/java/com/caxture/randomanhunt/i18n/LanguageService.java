package com.caxture.randomanhunt.i18n;

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
        recipient.sendMessage(render(recipient, key, placeholders, showChatPrefix.getAsBoolean()));
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

    public String text(CommandSender recipient, String key, Map<String, String> placeholders) {
        String message = resolve(languageOf(recipient), key);
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
        String message = resolve(language, key);
        if (!"prefix".equals(key)) {
            message = PrefixExpander.expand(message, resolve(language, "prefix"), includePrefix);
        }
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            message = message.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
        }
        return serializer.deserialize(message);
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
