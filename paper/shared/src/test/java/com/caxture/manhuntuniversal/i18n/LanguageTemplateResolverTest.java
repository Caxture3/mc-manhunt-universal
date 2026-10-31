package com.caxture.manhuntuniversal.i18n;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LanguageTemplateResolverTest {
    private static final List<String> USAGE_KEYS = List.of("command.usage", "game.start.usage",
            "config.usage", "team.usage", "config.structure-unlock-usage");
    private static final String LOCALIZED = "{prefix}用法：/mhu <join|start|status|stop|config>";

    @Test void oldExternalRootFallsBackToCurrentLocalizedUsageAndWarns() {
        for (String key : USAGE_KEYS) {
            var warnings = new ArrayList<String>();
            assertEquals(LOCALIZED, resolve(key, "{prefix}用法：/rmh <start|status|stop|config>", warnings));
            assertEquals(List.of(key), warnings);
        }
    }

    @Test void detectsCaseInsensitiveNamespacedAndColorTerminatedOldRoots() {
        for (String oldRoot : List.of("/RMH", "/RmH§e", "/randomanhunt:rmh", "/Old-Plugin:Rmh§a")) {
            var warnings = new ArrayList<String>();
            assertEquals(LOCALIZED, resolve("command.usage", "{prefix}Usage: " + oldRoot + " start", warnings));
            assertEquals(List.of("command.usage"), warnings);
        }
    }

    @Test void colorCodesBeforeOldRootsDoNotHideLegacyUsage() {
        for (String coloredRoot : List.of("§e/rmh", "§c/randomanhunt:RMH§r",
                "§x§1§2§3§4§5§6/rmh", "§l§e/rmh")) {
            var warnings = new ArrayList<String>();
            assertEquals(LOCALIZED, resolve("command.usage", "{prefix}" + coloredRoot + " start", warnings));
            assertEquals(List.of("command.usage"), warnings);
        }
    }

    @Test void colorStrippingForDetectionPreservesCurrentCustomTextAndExactTokenBoundaries() {
        for (String coloredRoot : List.of("§e/mhu", "§c/randomanhunt:MHU§r",
                "§x§1§2§3§4§5§6/mhu", "§e/rmhu", "§c/rmh§r-extra")) {
            var warnings = new ArrayList<String>();
            String custom = "{prefix}" + coloredRoot + " custom help";
            assertEquals(custom, resolve("command.usage", custom, warnings));
            assertTrue(warnings.isEmpty(), coloredRoot);
        }
    }

    @Test void currentCustomUsageAndOtherCommandTokensKeepTheirExternalText() {
        for (String root : List.of("/mhu", "/MHU", "/rmhu", "/rmh-extra", "/rmh_extra",
                "/rmh:extra", "/rmh/extra", "/old:rmhu")) {
            var warnings = new ArrayList<String>();
            String custom = "{prefix}Custom help: " + root + " join";
            assertEquals(custom, resolve("command.usage", custom, warnings));
            assertTrue(warnings.isEmpty(), root);
        }
    }

    @Test void oldRootsInOtherMessagesRemainUserControlled() {
        for (String key : List.of("command.unknown", "custom.usage", "config.saved")) {
            var warnings = new ArrayList<String>();
            String custom = "{prefix}Previously used /rmh start";
            assertEquals(custom, resolve(key, custom, warnings));
            assertTrue(warnings.isEmpty());
        }
    }

    @Test void placeholderValidationStillRejectsMalformedCurrentUsage() {
        var warnings = new ArrayList<String>();
        assertEquals(LOCALIZED, resolve("command.usage", "/mhu custom help", warnings));
        assertEquals(List.of("command.usage"), warnings);
    }

    private static String resolve(String key, String externalText, List<String> warnings) {
        var external = new YamlConfiguration();
        var localized = new YamlConfiguration();
        var english = new YamlConfiguration();
        external.set(key, externalText);
        localized.set(key, LOCALIZED);
        english.set(key, "{prefix}Usage: /mhu <join|start|status|stop|config>");
        String result = LanguageTemplateResolver.resolve(external, localized, english, key, warnings::add);
        assertEquals(externalText, external.getString(key), "External templates must remain unchanged");
        return result;
    }
}
