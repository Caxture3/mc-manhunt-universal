package com.caxture.manhuntuniversal.command;

import com.caxture.manhuntuniversal.api.config.ConfigSnapshot;
import com.caxture.manhuntuniversal.extension.config.ConfigurationRegistryService;
import com.caxture.manhuntuniversal.i18n.LanguageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.time.Duration;
import java.util.*;

/** Interactive commands use the same authoritative registry as extensions. */
final class RegistryConfigCommands {
    private final ConfigurationRegistryService registry;
    private final LanguageService language;
    private final StartPositionCommands starts;
    RegistryConfigCommands(ConfigurationRegistryService registry, LanguageService language) {
        this.registry = registry; this.language = language;
        this.starts = new StartPositionCommands(registry,language);
    }
    void handle(CommandSender sender, String[] args) {
        if(args.length>=2&&starts.handle(sender,args))return;
        if (args.length == 1) {
            language.send(sender, "config.header"); registry.list().forEach(snapshot -> show(sender, snapshot.key(), "")); return;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        if (registry.find(key).isEmpty()) { language.send(sender, "config.unknown", Map.of("key", key)); return; }
        if (args.length == 2) { show(sender, key, ""); return; }
        change(sender, key, String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
    }
    private void change(CommandSender sender, String key, String raw) {
        if (!sender.hasPermission(MhuCommand.ADMIN_PERMISSION)) { language.send(sender, "command.no-permission"); return; }
        try {
            var result = registry.updateCommand(key, registry.parse(key, raw));
            if (!result.successful()) {
                if (result.status() == com.caxture.manhuntuniversal.api.OperationStatus.SERVICE_UNAVAILABLE)
                    language.send(sender, "config.save-failed");
                else language.send(sender, "config.invalid", Map.of("reason", result.message()));
                return;
            }
            var snapshot = result.value().orElseThrow();
            language.send(sender, "config.saved", Map.of("key", name(sender,snapshot), "value", String.valueOf(snapshot.savedValue())));
            show(sender, key, "");
        } catch (IllegalArgumentException exception) {
            language.send(sender, "config.invalid", Map.of("reason", Objects.toString(exception.getMessage(), "Invalid value")));
        }
    }
    private void show(CommandSender sender, String key, String indentation) {
        if(key.equals(com.caxture.manhuntuniversal.config.PlayerStartPositions.KEY)){starts.show(sender,null);return;}
        var snapshot = registry.find(key).orElse(null); if (snapshot == null) return;
        if (snapshot.effectiveValue() instanceof Map<?, ?> map) {
            Component heading = language.chatComponent(sender, "config.map-start", Map.of("key", name(sender,snapshot)));
            String first = map.isEmpty() ? key : key + "." + map.keySet().iterator().next();
            language.sendComponent(sender, "core:config." + key, decorate(sender, snapshot, heading, first));
            int remaining = map.size();
            for (var item : map.entrySet()) {
                String childKey = key + "." + item.getKey();
                if (item.getValue() instanceof Map<?, ?>) { show(sender, childKey, indentation + "    "); continue; }
                var child = registry.find(childKey).orElseThrow();
                String label=key.equals(com.caxture.manhuntuniversal.config.RunnerStructureUnlockTimes.CONFIG_KEY)?String.valueOf(item.getKey()):name(sender,child);
                Component line = Component.text(indentation + "    " + label + ": " + item.getValue() + (--remaining > 0 ? "," : ""));
                language.sendComponent(sender, "core:config." + childKey, decorate(sender, child, line, childKey));
            }
            language.sendComponent(sender, "core:config." + key + ".end", Component.text(indentation + "}")); return;
        }
        Component line = language.chatComponent(sender, "config.entry", Map.of("key", name(sender,snapshot), "value", String.valueOf(snapshot.effectiveValue())));
        language.sendComponent(sender, "core:config." + key, decorate(sender, snapshot, line, key));
    }
    private Component decorate(CommandSender sender, ConfigSnapshot snapshot, Component line, String suggestedKey) {
        if(!Objects.equals(snapshot.effectiveValue(),snapshot.nextMatchValue()))line=line.append(language.component(sender,"config.next-value",Map.of("value",String.valueOf(snapshot.nextMatchValue()))));
        Component tooltip = Component.text(snapshot.key());
        String description=language.localizedValue(sender,"config.description."+snapshot.key(),snapshot.description());
        if(!description.isBlank())tooltip=tooltip.append(Component.newline()).append(Component.text(description));
        if (!Objects.equals(snapshot.savedValue(), snapshot.effectiveValue())) {
            line = line.append(language.component(sender, "config.value-overridden", Map.of("value", String.valueOf(snapshot.savedValue()))));
        }
        if (snapshot.timing() == com.caxture.manhuntuniversal.api.config.ConfigTiming.NEXT_MATCH)
            tooltip = tooltip.append(Component.newline()).append(language.component(sender, "config.next-match", Map.of()));
        if (snapshot.lockedBy().isPresent()) {
            Component detail = language.component(sender, "config.locked-detail", Map.of("owner", snapshot.lockedBy().orElseThrow(), "reason", snapshot.lockReason().orElse("")));
            line = line.append(Component.space()).append(detail); tooltip = tooltip.append(Component.newline()).append(detail);
        }
        ClickEvent action = null;
        if (sender instanceof Player owner) {
            if (snapshot.booleanValue()) {
                UUID id = owner.getUniqueId(); String key = snapshot.key();
                action = ClickEvent.callback(audience -> {
                    if (audience instanceof Player player && id.equals(player.getUniqueId())) {
                        var current = registry.find(key).orElse(null);
                        if (current != null) change(player, key, Boolean.toString(!((Boolean) current.savedValue())));
                    }
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).lifetime(Duration.ofHours(1)).build());
            } else action = ClickEvent.suggestCommand("/mhu config " + suggestedKey + " ");
        }
        return line.hoverEvent(HoverEvent.showText(tooltip)).clickEvent(action);
    }
    List<String> complete(CommandSender sender,String[] args) {
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 2) return java.util.stream.Stream.concat(registry.keys().stream(),starts.complete(sender,args).stream()).distinct().filter(key -> key.startsWith(prefix)).toList();
        var special=starts.complete(sender,args);if(!special.isEmpty())return special;
        return args.length >= 3 ? registry.complete(args[1].toLowerCase(Locale.ROOT), prefix) : List.of();
    }
    private static String locale(CommandSender sender) { return sender instanceof Player player ? player.locale().toLanguageTag() : "zh_cn"; }
    private String name(CommandSender sender,ConfigSnapshot snapshot) {
        return language.localizedValue(sender,"config.name."+snapshot.key()+(snapshot.effectiveValue() instanceof Map<?,?>?".title":""),snapshot.name());
    }
}
