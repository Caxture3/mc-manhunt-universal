package com.caxture.manhuntuniversal.command;

import com.caxture.manhuntuniversal.config.BooleanConfigKey;
import com.caxture.manhuntuniversal.config.ConfigurationService;
import com.caxture.manhuntuniversal.config.IntConfigKey;
import com.caxture.manhuntuniversal.game.GameManager;
import com.caxture.manhuntuniversal.game.GameSession;
import com.caxture.manhuntuniversal.game.TeamPreference;
import com.caxture.manhuntuniversal.game.TeamSelectionService;
import com.caxture.manhuntuniversal.i18n.LanguageService;
import com.caxture.manhuntuniversal.presentation.MatchPresentation;
import com.caxture.manhuntuniversal.runtime.ActiveGameCoordinator;
import com.caxture.manhuntuniversal.runtime.GameStartCoordinator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.ClickCallback;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MhuCommand implements CommandExecutor, TabCompleter {
    public static final String ADMIN_PERMISSION = "randomanhunt.admin";

    private final GameManager gameManager;
    private final GameStartCoordinator gameStart;
    private final ActiveGameCoordinator activeGame;
    private final ConfigurationService configuration;
    private final LanguageService language;
    private final MatchPresentation presentation;
    private final Clock clock;
    private final TeamSelectionPresenter teamPresenter;

    public MhuCommand(
            GameManager gameManager,
            GameStartCoordinator gameStart,
            ActiveGameCoordinator activeGame,
            ConfigurationService configuration,
            LanguageService language,
            MatchPresentation presentation,
            TeamSelectionPresenter teamPresenter,
            Clock clock
    ) {
        this.gameManager = gameManager;
        this.gameStart = gameStart;
        this.activeGame = activeGame;
        this.configuration = configuration;
        this.language = language;
        this.presentation = presentation;
        this.clock = clock;
        this.teamPresenter = teamPresenter;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean readOnly = args.length == 0 || args[0].equalsIgnoreCase("status")
                || args[0].equalsIgnoreCase("config") && args.length <= 2;
        if (!readOnly && activeGame.extensionHooks().mutationActive()) {
            language.send(sender, "command.busy");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("join")) {
            join(sender, args);
            return true;
        }
        if (!CommandAccessPolicy.adminAllowed(sender.hasPermission(ADMIN_PERMISSION))) {
            language.send(sender, "command.no-permission");
            return true;
        }
        if (args.length == 0) {
            language.send(sender, "command.usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> start(sender, args);
            case "status" -> status(sender, args);
            case "stop" -> stop(sender, args);
            case "config" -> config(sender, args);
            default -> language.send(sender, "command.unknown");
        }
        return true;
    }

    private void join(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            language.send(sender, "team.player-only");
            return;
        }
        if (gameManager.teamSelections().forcedRandom()) {
            language.send(player, "team.disabled");
            return;
        }
        if (args.length == 1) {
            teamPresenter.show(player);
            return;
        }
        TeamPreference preference;
        try {
            if (args.length != 2) throw new IllegalArgumentException();
            preference = TeamPreference.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            language.send(player, "team.usage");
            return;
        }
        TeamSelectionService.ChoiceResult result = gameManager.teamSelections().choose(
                player.getUniqueId(), preference, configuration.get(IntConfigKey.RUNNER_COUNT));
        switch (result) {
            case DISABLED -> language.send(player, "team.disabled");
            case FULL -> language.send(player, "team.runner-full", Map.of(
                    "count", Integer.toString(configuration.get(IntConfigKey.RUNNER_COUNT))));
            case LOCKED -> language.send(player, "team.locked");
            case CHANGED, UNCHANGED -> {
                if (configuration.get(BooleanConfigKey.HIDE_ROLE_ASSIGNMENTS)) {
                    language.send(player, "team.selected-hidden");
                } else {
                    language.send(player, "team.selected", Map.of(
                            "team", language.text(player, "team.name." + preference.name().toLowerCase(Locale.ROOT))));
                }
            }
        }
    }

    private void start(CommandSender sender, String[] args) {
        if (args.length > 2) {
            language.send(sender, "game.start.usage");
            return;
        }
        int seconds = 0;
        if (args.length == 2) {
            try {
                seconds = CommandArguments.countdown(args[1]);
            } catch (CommandArguments.InvalidArgument exception) {
                language.send(sender, exception.error() == CommandArguments.Error.NOT_INTEGER
                        ? "game.start.countdown-not-integer" : "game.start.countdown-out-of-range");
                return;
            }
        }
        gameStart.start(sender, seconds);
    }

    private void status(CommandSender sender, String[] args) {
        if (args.length != 1) {
            language.send(sender, "command.usage");
            return;
        }
        GameSession session = gameManager.activeSession().orElse(null);
        if (session == null) {
            presentation.showIdleStatus(sender);
            return;
        }
        presentation.showStatus(
                sender, session, activeGame.countdownRemaining(session.id()), Instant.now(clock));
    }

    private void stop(CommandSender sender, String[] args) {
        if (args.length != 1) {
            language.send(sender, "command.usage");
            return;
        }
        if (!activeGame.stopAdmin()) {
            language.send(sender, "game.stop.none");
        }
    }

    private void config(CommandSender sender, String[] args) {
        if (args.length == 1) {
            language.send(sender, "config.header");
            for (IntConfigKey key : IntConfigKey.values()) {
                sendConfigEntry(sender, key.configKey(), Integer.toString(configuration.get(key)));
            }
            for (BooleanConfigKey key : BooleanConfigKey.values()) {
                sendConfigEntry(sender, key.configKey(), Boolean.toString(configuration.get(key)));
            }
            return;
        }
        String requested = args[1].toLowerCase(Locale.ROOT);
        IntConfigKey integerKey = IntConfigKey.find(requested).orElse(null);
        BooleanConfigKey booleanKey = BooleanConfigKey.find(requested).orElse(null);
        if (integerKey == null && booleanKey == null) {
            language.send(sender, "config.unknown", Map.of("key", args[1]));
            return;
        }
        if (args.length == 2) {
            sendConfigEntry(sender, requested, integerKey != null
                    ? Integer.toString(configuration.get(integerKey))
                    : Boolean.toString(configuration.get(booleanKey)));
            return;
        }
        if (args.length != 3) {
            language.send(sender, "config.usage");
            return;
        }
        saveConfig(sender, requested, integerKey, booleanKey, args[2]);
    }

    private boolean saveConfig(CommandSender sender, String requested, IntConfigKey integerKey,
                               BooleanConfigKey booleanKey, String raw) {
        if (activeGame.extensionHooks().mutationActive()) {
            language.send(sender, "command.busy");
            return false;
        }
        try {
            boolean saved;
            String value;
            if (integerKey != null) {
                int parsed = CommandArguments.integer(integerKey, raw);
                saved = configuration.update(integerKey, parsed);
                value = Integer.toString(parsed);
            } else {
                boolean parsed = CommandArguments.bool(raw);
                saved = configuration.update(booleanKey, parsed);
                value = Boolean.toString(parsed);
            }
            if (saved) {
                language.send(sender, "config.saved", Map.of(
                        "key", language.text(sender, "config.name." + requested), "value", value));
            } else {
                language.send(sender, "config.save-failed");
            }
            return saved;
        } catch (CommandArguments.InvalidArgument exception) {
            language.send(sender, "config.invalid", Map.of(
                    "reason", argumentError(sender, exception.error(), integerKey)));
        }
        return false;
    }

    private String argumentError(
            CommandSender sender,
            CommandArguments.Error error,
            IntConfigKey integerKey
    ) {
        return switch (error) {
            case NOT_INTEGER -> language.text(sender, "config.reason.not-integer");
            case INVALID_BOOLEAN -> language.text(sender, "config.reason.invalid-boolean");
            case OUT_OF_RANGE -> language.text(sender, "config.reason.out-of-range", Map.of(
                    "min", Integer.toString(integerKey.minimum()),
                    "max", Integer.toString(integerKey.maximum())));
        };
    }

    private void sendConfigEntry(CommandSender sender, String key, String value) {
        Component line = language.chatComponent(sender, "config.entry", Map.of(
                "key", language.text(sender, "config.name." + key), "value", value));
        ClickEvent click = null;
        if (sender instanceof Player owner) {
            var ownerId = owner.getUniqueId();
            BooleanConfigKey booleanKey = BooleanConfigKey.find(key).orElse(null);
            click = booleanKey == null ? ClickEvent.suggestCommand("/mhu config " + key + " ")
                    : ClickEvent.callback(audience -> {
                        if (!(audience instanceof Player player)
                                || !ownerId.equals(player.getUniqueId())) return;
                        ConfigToggleAction.Result result = ConfigToggleAction.toggle(
                                () -> player.hasPermission(ADMIN_PERMISSION),
                                () -> configuration.get(booleanKey),
                                next -> saveConfig(player, key, null, booleanKey, Boolean.toString(next)));
                        if (result == ConfigToggleAction.Result.DENIED) {
                            language.send(player, "command.no-permission");
                        } else if (result == ConfigToggleAction.Result.CHANGED) {
                            sendConfigEntry(player, key, Boolean.toString(configuration.get(booleanKey)));
                        }
                    }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES)
                            .lifetime(Duration.ofHours(1)).build());
        }
        sender.sendMessage(InteractiveMessages.configEntry(line, key, click));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        boolean admin = CommandAccessPolicy.adminAllowed(sender.hasPermission(ADMIN_PERMISSION));
        List<String> candidates = new ArrayList<>();
        if (args.length == 1) {
            if (sender instanceof Player && !gameManager.teamSelections().forcedRandom()) candidates.add("join");
            if (admin) candidates.addAll(List.of("start", "status", "stop", "config"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("join") && sender instanceof Player
                && !gameManager.teamSelections().forcedRandom()) {
            candidates.addAll(List.of("hunter", "runner", "random"));
        } else if (!admin) {
            return List.of();
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            candidates.addAll(List.of("5", "10", "30", "60"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("config")) {
            Arrays.stream(IntConfigKey.values()).map(IntConfigKey::configKey).forEach(candidates::add);
            Arrays.stream(BooleanConfigKey.values()).map(BooleanConfigKey::configKey).forEach(candidates::add);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("config")
                && BooleanConfigKey.find(args[1].toLowerCase(Locale.ROOT)).isPresent()) {
            candidates.addAll(List.of("true", "false"));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return candidates.stream().filter(value -> value.startsWith(prefix)).toList();
    }
}
