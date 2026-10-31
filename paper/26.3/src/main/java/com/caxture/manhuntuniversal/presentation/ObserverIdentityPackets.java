package com.caxture.manhuntuniversal.presentation;
import net.kyori.adventure.text.format.NamedTextColor;
import java.lang.reflect.Method;
import java.util.*;
/** Minecraft 26.3 uses Optional<TeamColor> for scoreboard team colors. */
final class ObserverIdentityPackets extends ObserverIdentityPacketSupport {
    @Override protected void color(Object team, NamedTextColor color) throws ReflectiveOperationException {
        Lazy.SET.invoke(team, Optional.of(Lazy.COLOR.getField(color.toString().toUpperCase(Locale.ROOT)).get(null)));
    }
    private static final class Lazy {
        static final Class<?> COLOR;
        static final Method SET;
        static { try {
            COLOR = Class.forName("net.minecraft.world.scores.TeamColor");
            SET = Class.forName("net.minecraft.world.scores.PlayerTeam").getMethod("setColor", Optional.class);
        } catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); } }
    }
}
