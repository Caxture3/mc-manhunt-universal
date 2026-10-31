package com.caxture.manhuntuniversal.optimization;

import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class BlazeRodLootingListenerTest {
    private boolean running = true;
    private int configured = 5;
    private int existing;
    private boolean nativeLootAllowed = true;
    private boolean playerCredit = true;
    private int created;
    private int weaponReads;
    private final Player player = proxy(Player.class, (method, args) -> {
        throw new AssertionError("Must never mutate or temporarily enchant the player: " + method);
    });
    private Entity attacker = player;
    private final Blaze blaze = proxy(Blaze.class, (method, args) -> switch (method) {
        case "getKiller" -> playerCredit ? player : null;
        default -> throw new AssertionError(method);
    });
    private final DamageSource damage = proxy(DamageSource.class, (method, args) -> switch (method) {
        case "getCausingEntity" -> attacker;
        case "getDirectEntity" -> throw new AssertionError("Use the responsible attacker, not the projectile");
        default -> throw new AssertionError(method);
    });
    private final BlazeRodLootingListener listener = new BlazeRodLootingListener(() -> running, () -> configured,
            new Random(1) {
                @Override public int nextInt(int bound) { assertEquals(2, bound); return 1; }
                @Override public float nextFloat() { return .99f; }
            }, new BlazeRodLootingListener.LootAccess() {
                @Override public boolean allowed(Blaze entity) { return nativeLootAllowed; }
                @Override public int looting(Player killer) { assertSame(player, killer); weaponReads++; return existing; }
                @Override public ItemStack rods(int count) { created++; return BlazeRodDropsTest.stack(Material.BLAZE_ROD, count, false); }
            });

    @Test void playerAndPlayerProjectileKillsUseResponsiblePlayersLevelAndLeaveXpAndSecondaryLoot() {
        var diamond = BlazeRodDropsTest.stack(Material.DIAMOND, 2, false);
        var event = event(blaze, List.of(diamond, BlazeRodDropsTest.stack(Material.BLAZE_ROD, 1, false)));
        listener.onDeath(event);
        assertEquals(10, event.getDroppedExp());
        assertSame(diamond, event.getDrops().getFirst());
        assertEquals(6, event.getDrops().getLast().getAmount());
        assertEquals(1, weaponReads);
        assertEquals(1, created);
    }

    @Test void nativeZeroDropCanBecomeRodsButExistingEqualOrHigherLootingIsUntouched() {
        var empty = event(blaze, List.of());
        listener.onDeath(empty);
        assertEquals(6, empty.getDrops().getFirst().getAmount());
        for (int level : new int[]{5, 8}) {
            existing = level;
            var original = BlazeRodDropsTest.stack(Material.BLAZE_ROD, 2, false);
            var event = event(blaze, List.of(original));
            listener.onDeath(event);
            assertEquals(List.of(original), event.getDrops());
        }
        assertEquals(1, created);
    }

    @Test void zeroIdleCancelledAndNonBlazeDeathsNeverReroll() {
        for (int mode = 0; mode < 4; mode++) {
            running = mode != 0;
            configured = mode == 1 ? 0 : 5;
            LivingEntity entity = mode == 3 ? proxy(Zombie.class, (method, args) -> { throw new AssertionError(method); }) : blaze;
            var event = event(entity, List.of());
            event.setCancelled(mode == 2);
            listener.onDeath(event);
            assertTrue(event.getDrops().isEmpty());
        }
        assertEquals(0, created);
        assertEquals(0, weaponReads);
    }

    @Test void environmentalOrPetKillsDoNotBorrowARecentPlayersLooting() {
        for (Entity cause : new Entity[]{null, proxy(Zombie.class, (method, args) -> { throw new AssertionError(method); })}) {
            attacker = cause;
            var event = event(blaze, List.of());
            listener.onDeath(event);
            assertTrue(event.getDrops().isEmpty());
        }
        attacker = player;
        playerCredit = false;
        var noCredit = event(blaze, List.of());
        listener.onDeath(noCredit);
        assertTrue(noCredit.getDrops().isEmpty());
        assertEquals(0, weaponReads);
    }

    @Test void disabledNativeLootOrObviouslyCustomPayloadRemainsUntouched() {
        nativeLootAllowed = false;
        var event = event(blaze, List.of());
        listener.onDeath(event);
        assertTrue(event.getDrops().isEmpty());
        nativeLootAllowed = true;
        var customized = BlazeRodDropsTest.stack(Material.BLAZE_ROD, 30, false);
        var customEvent = event(blaze, List.of(customized));
        listener.onDeath(customEvent);
        assertSame(customized, customEvent.getDrops().getFirst());
        assertEquals(0, created);
    }

    private EntityDeathEvent event(LivingEntity entity, List<ItemStack> drops) {
        return new EntityDeathEvent(entity, damage, new ArrayList<>(drops), 10);
    }
    @FunctionalInterface interface Invoke { Object invoke(String method, Object[] args); }
    private static <T> T proxy(Class<T> type, Invoke invoke) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (value, method, args) -> invoke.invoke(method.getName(), args)));
    }
}
