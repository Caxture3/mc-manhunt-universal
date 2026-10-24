package com.caxture.randomanhunt.command;

import com.caxture.randomanhunt.config.BooleanConfigKey;
import com.caxture.randomanhunt.config.ConfigurationService;
import com.caxture.randomanhunt.config.IntConfigKey;
import com.caxture.randomanhunt.game.GameManager;
import com.caxture.randomanhunt.game.GameSession;
import com.caxture.randomanhunt.i18n.LanguageService;
import com.caxture.randomanhunt.presentation.MatchPresentation;
import com.caxture.randomanhunt.runtime.ActiveGameCoordinator;
import com.caxture.randomanhunt.runtime.GameStartCoordinator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RmhCommand implements CommandExecutor, TabCompleter {
    public static final String ADMIN_PERMISSION = "randomanhunt.admin";

    private final GameManager gameManager;
    private final GameStartCoordinator gameStart;
    private final ActiveGameCoordinator activeGame;
    private final ConfigurationService configuration;
    private final LanguageService language;
    private final MatchPresentation presentation;
    private final Clock clock;

    public RmhCommand(
            GameManager gameManager,
            GameStartCoordinator gameStart,
            ActiveGameCoordinator activeGame,
            ConfigurationService configuration,
            LanguageService language,
            MatchPresentation presentation,
            Clock clock
    ) {
        this.gameManager = gameManager;
        this.gameStart = gameStart;
        this.activeGame = activeGame;
        this.configuration = configuration;
        this.language = language;
        this.presentation = presentation;
        this.clock = clock;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
        try {
            boolean saved;
            String value;
            if (integerKey != null) {
                int parsed = CommandArguments.integer(integerKey, args[2]);
                saved = configuration.update(integerKey, parsed);
                value = Integer.toString(parsed);
            } else {
                boolean parsed = CommandArguments.bool(args[2]);
                saved = configuration.update(booleanKey, parsed);
                value = Boolean.toString(parsed);
            }
            if (saved) {
                if (booleanKey != null) {
                    activeGame.configurationChanged(booleanKey);
                }
                language.send(sender, "config.saved", Map.of("key", requested, "value", value));
            } else {
                language.send(sender, "config.save-failed");
            }
        } catch (CommandArguments.InvalidArgument exception) {
            language.send(sender, "config.invalid", Map.of(
                    "reason", argumentError(sender, exception.error(), integerKey)));
        }
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
        language.send(sender, "config.entry", Map.of("key", key, "value", value));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (!CommandAccessPolicy.adminAllowed(sender.hasPermission(ADMIN_PERMISSION))) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>();
        if (args.length == 1) {
            candidates.addAll(List.of("start", "status", "stop", "config"));
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
