package com.caxture.manhuntuniversal.presentation;
import net.kyori.adventure.text.format.NamedTextColor;
import java.lang.reflect.Method;
import java.util.Locale;
/** Minecraft 1.21.11 uses ChatFormatting for scoreboard team colors. */
final class ObserverIdentityPackets extends ObserverIdentityPacketSupport {
    @Override protected void color(Object team, NamedTextColor color) throws ReflectiveOperationException {
        Lazy.SET.invoke(team, Lazy.COLOR.getField(color.toString().toUpperCase(Locale.ROOT)).get(null));
    }
    private static final class Lazy {
        static final Class<?> COLOR;
        static final Method SET;
        static { try {
            COLOR = Class.forName("net.minecraft.ChatFormatting");
            SET = Class.forName("net.minecraft.world.scores.PlayerTeam").getMethod("setColor", COLOR);
        } catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); } }
    }
}
