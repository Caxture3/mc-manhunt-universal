package com.caxture.manhuntuniversal.presentation;
import com.caxture.manhuntuniversal.api.paper.presentation.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ObserverIdentityTest {
    @Test void observersHaveIndependentStylesAndStaleRevisionCannotReinstallOldOutput() {
        var state = new ObserverIdentityState();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), target = UUID.randomUUID();
        var red = new ObserverIdentityStyle(Component.text("Runner"), true, 1, Component.empty(), Component.empty(), NamedTextColor.RED, true);
        var hidden = new ObserverIdentityStyle(Component.text("Unknown"), false, 9, Component.empty(), Component.empty(), NamedTextColor.WHITE, false);
        long revision = state.invalidate();
        assertTrue(state.put(first, target, red, revision)); assertTrue(state.put(second, target, hidden, revision));
        assertEquals(red, state.get(first, target).orElseThrow()); assertEquals(hidden, state.get(second, target).orElseThrow());
        state.invalidate(); assertFalse(state.put(second, target, red, revision));
        assertEquals(hidden, state.get(second, target).orElseThrow());
        assertEquals(2, state.clear().size()); assertTrue(state.clear().isEmpty());
    }
    @Test void playerRemovalDropsBothObserverAndTargetPairsWhileKeepingOthers() {
        var state = new ObserverIdentityState(); UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var style = new ObserverIdentityStyle(Component.text("name"), true, 0, Component.empty(), Component.empty(), NamedTextColor.WHITE, true);
        state.put(a,b,style,0); state.put(b,c,style,0); state.put(a,c,style,0);
        assertEquals(2, state.removePlayer(b).size()); assertEquals(style, state.get(a,c).orElseThrow());
    }
}
