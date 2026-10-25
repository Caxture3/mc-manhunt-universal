package com.caxture.randomanhunt.optimization;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OreFortuneDropsTest {
    @Test
    void virtualFortuneKeepsSilkTouchAndNeverStacksWithOrDowngradesExistingFortune() {
        assertEquals(0, OreFortuneDrops.upgradedLevel(0, 0, false));
        assertEquals(10, OreFortuneDrops.upgradedLevel(10, 3, false));
        assertEquals(0, OreFortuneDrops.upgradedLevel(3, 3, false));
        assertEquals(0, OreFortuneDrops.upgradedLevel(3, 7, false));
        assertEquals(0, OreFortuneDrops.upgradedLevel(10, 0, true));
    }

    @Test
    void onlyActualOreDropsAreEligible() {
        assertEquals(Material.LAPIS_LAZULI, OreFortuneDrops.oreDrop(Material.DEEPSLATE_LAPIS_ORE));
        assertEquals(Material.REDSTONE, OreFortuneDrops.oreDrop(Material.REDSTONE_ORE));
        assertEquals(Material.GOLD_NUGGET, OreFortuneDrops.oreDrop(Material.NETHER_GOLD_ORE));
        assertNull(OreFortuneDrops.oreDrop(Material.RAW_IRON_BLOCK));
        assertNull(OreFortuneDrops.oreDrop(Material.GLOWSTONE));
        assertNull(OreFortuneDrops.oreDrop(Material.WHEAT));
    }

    @Test
    void nativeOversizedLootIsSplitWithoutDoublingOriginalDropsOrLosingAttachedTorch() {
        Fixture fixture = new Fixture();
        Item torch = fixture.item(new Stack(Material.TORCH, 1));
        fixture.items.add(fixture.item(new Stack(Material.LAPIS_LAZULI, 6)));
        fixture.items.add(torch);
        Stack nativeLoot = new Stack(Material.LAPIS_LAZULI, 132);

        OreFortuneDrops.replace(fixture.event, Material.LAPIS_LAZULI, List.of(nativeLoot));

        assertEquals(List.of(1, 64, 64, 4), fixture.items.stream()
                .map(item -> item.getItemStack().getAmount()).toList());
        assertSame(torch, fixture.items.getFirst());
        assertEquals(132, nativeLoot.getAmount(), "Native loot snapshots must remain unchanged");
        assertEquals(3, fixture.created, "All new items must be created unspawned for the event");
    }

    @Test
    void cancelledOrSuppressedDropsNeverCreateReplacementEntities() {
        Fixture fixture = new Fixture();
        Item original = fixture.item(new Stack(Material.LAPIS_LAZULI, 6));
        fixture.items.add(original);
        fixture.event.setCancelled(true);
        OreFortuneDrops.replace(fixture.event, Material.LAPIS_LAZULI,
                List.of(new Stack(Material.LAPIS_LAZULI, 100)));
        assertEquals(List.of(original), fixture.items);
        assertEquals(0, fixture.created);

        fixture.event.setCancelled(false);
        fixture.items.clear();
        OreFortuneDrops.replace(fixture.event, Material.LAPIS_LAZULI,
                List.of(new Stack(Material.LAPIS_LAZULI, 100)));
        assertTrue(fixture.items.isEmpty());
        assertEquals(0, fixture.created);
    }

    @Test
    void rerolledSecondaryLootDoesNotDuplicateOriginalSecondaryDrops() {
        Fixture fixture = new Fixture();
        fixture.items.add(fixture.item(new Stack(Material.LAPIS_LAZULI, 6)));
        Item secondary = fixture.item(new Stack(Material.DIAMOND, 1));
        fixture.items.add(secondary);

        OreFortuneDrops.replace(fixture.event, Material.LAPIS_LAZULI,
                List.of(new Stack(Material.LAPIS_LAZULI, 20), new Stack(Material.DIAMOND, 1)));

        assertEquals(2, fixture.items.size());
        assertSame(secondary, fixture.items.getFirst());
        assertEquals(20, fixture.items.getLast().getItemStack().getAmount());
    }

    /** ItemStack boundary fake avoids requiring a running Bukkit registry/server. */
    private static final class Stack extends ItemStack {
        private final Material type;
        private int amount;

        Stack(Material type, int amount) { this.type = type; this.amount = amount; }
        @Override public Material getType() { return type; }
        @Override public int getAmount() { return amount; }
        @Override public void setAmount(int amount) { this.amount = amount; }
        @Override public int getMaxStackSize() { return 64; }
        @Override public ItemStack clone() { return new Stack(type, amount); }
    }

    private static final class Fixture {
        final List<Item> items = new ArrayList<>();
        int created;
        final World world = proxy(World.class, (method, args) -> switch (method) {
            case "createEntity" -> { created++; yield item(null); }
            default -> throw new AssertionError("Unexpected world operation: " + method);
        });
        final Location location = new Location(world, 2.5, 64.5, 3.5);
        final Block block = proxy(Block.class, (method, args) -> switch (method) {
            case "getWorld" -> world;
            case "getLocation" -> location;
            default -> throw new AssertionError(method);
        });
        final BlockState state = proxy(BlockState.class, (method, args) -> switch (method) {
            case "getWorld" -> world;
            case "getLocation" -> location;
            default -> throw new AssertionError(method);
        });
        final Player player = proxy(Player.class, (method, args) -> { throw new AssertionError(method); });
        final BlockDropItemEvent event = new BlockDropItemEvent(block, state, player, items);

        Item item(ItemStack initial) {
            ItemStack[] stack = {initial};
            return proxy(Item.class, (method, args) -> switch (method) {
                case "getItemStack" -> stack[0];
                case "setItemStack" -> { stack[0] = (ItemStack) args[0]; yield null; }
                case "getLocation" -> location;
                default -> throw new AssertionError(method);
            });
        }
    }

    private interface Invocation { Object call(String method, Object[] args); }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (object, method, args) -> switch (method.getName()) {
                    case "equals" -> object == args[0];
                    case "hashCode" -> System.identityHashCode(object);
                    case "toString" -> type.getSimpleName();
                    default -> invocation.call(method.getName(), args);
                }));
    }
}
