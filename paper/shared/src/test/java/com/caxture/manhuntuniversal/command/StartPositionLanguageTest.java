package com.caxture.manhuntuniversal.command;

import com.caxture.manhuntuniversal.i18n.LanguageTemplateResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class StartPositionLanguageTest {
    @Test void configurationHeadingResolvesInBothBundledLanguagesWithoutWarnings() throws Exception {
        var english = bundled("en_us");
        for (var locale : Map.of("en_us", "Player start positions", "zh_cn", "玩家开局位置").entrySet()) {
            var localized = bundled(locale.getKey());
            var warnings = new ArrayList<String>();
            String heading = LanguageTemplateResolver.resolve(localized, localized, english,
                    StartPositionCommands.TITLE_LANGUAGE_KEY, warnings::add);
            assertEquals(locale.getValue(), heading);
            assertTrue(warnings.isEmpty(), warnings.toString());
        }
    }

    @Test void oldExternalFilesFallBackAndCorrectExternalOverridesRemainUnchanged() throws Exception {
        var external = new YamlConfiguration();
        external.set("config.header", "Custom configuration");
        var warnings = new ArrayList<String>();
        assertEquals("玩家开局位置", LanguageTemplateResolver.resolve(external, bundled("zh_cn"), bundled("en_us"),
                StartPositionCommands.TITLE_LANGUAGE_KEY, warnings::add));
        assertEquals(List.of(StartPositionCommands.TITLE_LANGUAGE_KEY), warnings);
        assertFalse(external.contains("config.name.player-start-pos.title"));

        external.set("config.name.player-start-pos.title", "我的开局位置");
        warnings.clear();
        assertEquals("我的开局位置", LanguageTemplateResolver.resolve(external, bundled("zh_cn"), bundled("en_us"),
                StartPositionCommands.TITLE_LANGUAGE_KEY, warnings::add));
        assertTrue(warnings.isEmpty());
        assertEquals("我的开局位置", external.getString("config.name.player-start-pos.title"));
    }

    private static YamlConfiguration bundled(String locale) throws Exception {
        try (var input = StartPositionLanguageTest.class.getResourceAsStream("/lang/" + locale + ".yml")) {
            assertNotNull(input);
            var yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return yaml;
        }
    }
}
