package com.caxture.manhuntuniversal.api;

import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationTargetException;
import static org.junit.jupiter.api.Assertions.*;

class Api11CompatibilityTest {
    @Test void precompiledLegacyImplementationLinksAndNewEntrypointsHaveDefaults() throws Exception {
        var legacy = Class.forName("compatibility.LegacyApiConsumer");
        assertEquals("fixture:legacy:true", legacy.getMethod("probe").invoke(null));
        Object instance = legacy.getConstructor().newInstance();
        for (String entry : new String[]{"configuration", "features", "texts", "tracking"}) {
            var method = ManhuntUniversalApi.class.getMethod(entry);
            assertTrue(method.isDefault());
            var error = assertThrows(InvocationTargetException.class, () -> method.invoke(instance));
            assertInstanceOf(UnsupportedOperationException.class, error.getCause());
        }
    }
}
