package example.mhu;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.config.*;
import java.util.*;

/** A group is saved as one validated value, even when the command changes one leaf. */
public final class ConfigurationGroupExample {
    private ConfigurationGroupExample() { }
    public static ExtensionResult<ExtensionHandle> register(ConfigurationRegistry registry, ExtensionRegistration owner,
                                                            ConfigStorage<Map<String, Object>> atomicStorage) {
        Map<String, ConfigField<?>> fields = new LinkedHashMap<>();
        fields.put("minimum", new ConfigField<>(LocalizedText.bilingual("Minimum interval", "最短间隔"), ConfigTypes.integers(1, 3600), "s"));
        fields.put("maximum", new ConfigField<>(LocalizedText.bilingual("Maximum interval", "最长间隔"), ConfigTypes.integers(1, 3600), "s"));
        ConfigType<Map<String, Object>> schema = ConfigTypes.group(fields);
        ConfigType<Map<String, Object>> orderedInterval = new ConfigType<>() {
            public Map<String, Object> parse(String text) { return schema.parse(text); }
            public Map<String, ConfigField<?>> fields() { return schema.fields(); }
            public Map<String, Object> validate(Object value) {
                var normalized = schema.validate(value);
                if ((int) normalized.get("minimum") > (int) normalized.get("maximum"))
                    throw new IllegalArgumentException("minimum must not exceed maximum");
                return normalized;
            }
        };
        return registry.register(owner, new ConfigDefinition<>("example:interval",
                LocalizedText.bilingual("Interval", "间隔"), LocalizedText.of("seconds"),
                Map.of("minimum", 60, "maximum", 180), orderedInterval, atomicStorage, ConfigTiming.NEXT_MATCH, "s"));
    }
}
