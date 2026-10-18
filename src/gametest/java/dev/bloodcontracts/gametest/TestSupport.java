package dev.bloodcontracts.gametest;

import dev.bloodcontracts.config.BCConfig;
import dev.bloodcontracts.core.Bounty;
import dev.bloodcontracts.core.BountyManager;
import dev.bloodcontracts.core.BountyResult;
import dev.bloodcontracts.core.PendingDelivery;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Shared GameTest plumbing: reset, marked items, item conservation, damage. */
public final class TestSupport {
    private TestSupport() {}

    /** Call first in every test: fresh bounty state, default config, no leftover players, PvP on. */
    public static BountyManager reset(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TestPlayers.logoutAll();
        resetConfig();
        server.setPvpAllowed(true);
        BountyManager manager = BountyManager.get(server);
        manager.resetForTests();
        return manager;
    }

    private static void resetConfig() {
        for (ModConfigSpec.ConfigValue<?> value : List.of(BCConfig.ATTRIBUTION_WINDOW_TICKS, BCConfig.CANNON_OPERATOR_WINDOW_TICKS,
                BCConfig.CANCEL_LOCK_SECONDS, BCConfig.MAX_ACTIVE_BOUNTIES_PER_SPONSOR, BCConfig.REWARD_BLACKLIST,
                BCConfig.CREDIT_TAMED_PETS, BCConfig.SHOW_SPONSOR, BCConfig.HISTORY_SIZE)) {
            restore(value);
        }
        BCConfig.DEBUG_LOGGING.set(true);
    }

    private static <T> void restore(ModConfigSpec.ConfigValue<T> value) {
        if (!value.get().equals(value.getDefault())) {
            value.set(value.getDefault());
        }
    }

    /** A stack carrying a unique marker, so conservation counts only this test's items. */
    public static ItemStack marked(Item item, int count, UUID marker) {
        ItemStack stack = new ItemStack(item, count);
        CompoundTag tag = new CompoundTag();
        tag.putUUID("bc_test_marker", marker);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static boolean isMarked(ItemStack stack, UUID marker) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().hasUUID("bc_test_marker") && data.copyTag().getUUID("bc_test_marker").equals(marker);
    }

    /** Puts the stack in the player's main hand. */
    public static void hold(ServerPlayer player, ItemStack stack) {
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, stack);
    }

    public static BountyResult<Bounty> placeFromHand(BountyManager manager, ServerPlayer sponsor, ServerPlayer target) {
        Inventory inv = sponsor.getInventory();
        return manager.create(sponsor, target.getUUID(), inv, inv.selected);
    }

    /**
     * Counts marked items everywhere they can legitimately be: player inventories and cursors, escrow, pending
     * deliveries and item entities around the test.
     */
    public static int countMarked(GameTestHelper helper, BountyManager manager, UUID marker) {
        int total = 0;
        for (ServerPlayer p : helper.getLevel().getServer().getPlayerList().getPlayers()) {
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                total += countIf(inv.getItem(i), marker);
            }
            total += countIf(p.containerMenu.getCarried(), marker);
            for (var slot : p.containerMenu.slots) {
                if (slot.container != inv) {
                    total += countIf(slot.getItem(), marker);
                }
            }
        }
        for (Bounty b : manager.activeBounties()) {
            total += countIf(b.reward(), marker);
        }
        for (PendingDelivery d : manager.data().deliveries()) {
            total += countIf(d.stack(), marker);
        }
        AABB area = helper.getBounds().inflate(64);
        for (ItemEntity e : helper.getLevel().getEntities(EntityType.ITEM, area, e -> e.isAlive())) {
            total += countIf(e.getItem(), marker);
        }
        return total;
    }

    private static int countIf(ItemStack stack, UUID marker) {
        return isMarked(stack, marker) ? stack.getCount() : 0;
    }

    public static void assertConserved(GameTestHelper helper, BountyManager manager, UUID marker, int expected) {
        int actual = countMarked(helper, manager, marker);
        helper.assertTrue(actual == expected, "conservation broken: expected " + expected + " marked items, found " + actual);
    }

    /** Applies damage as a fresh hit (no invulnerability frames from a previous hit). */
    public static boolean hit(LivingEntity victim, DamageSource source, float amount) {
        victim.invulnerableTime = 0;
        return victim.hurt(source, amount);
    }

    public static int countInInventory(ServerPlayer player, UUID marker) {
        int total = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            total += countIf(inv.getItem(i), marker);
        }
        return total;
    }
}
