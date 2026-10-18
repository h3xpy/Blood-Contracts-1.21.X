package dev.bloodcontracts.gametest;

import static dev.bloodcontracts.gametest.TestSupport.assertConserved;
import static dev.bloodcontracts.gametest.TestSupport.hit;
import static dev.bloodcontracts.gametest.TestSupport.hold;
import static dev.bloodcontracts.gametest.TestSupport.marked;
import static dev.bloodcontracts.gametest.TestSupport.placeFromHand;

import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.config.BCConfig;
import dev.bloodcontracts.core.Bounty;
import dev.bloodcontracts.core.BountyError;
import dev.bloodcontracts.core.BountyManager;
import dev.bloodcontracts.core.BountyResult;
import dev.bloodcontracts.core.BountyState;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Creation, escrow, cancellation and history. Each test runs in its own batch because they share global state. */
@GameTestHolder(BloodContracts.MODID)
@PrefixGameTestTemplate(false)
public class LifecycleTests {
    private static final String T = BloodContractsGameTests.TEMPLATE;

    @GameTest(template = T, batch = "create_escrow")
    public static void createMovesExactStackToEscrow(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        UUID marker = UUID.randomUUID();
        ItemStack reward = marked(Items.DIAMOND_SWORD, 1, marker);
        reward.set(DataComponents.CUSTOM_NAME, Component.literal("Bloodletter"));
        reward.setDamageValue(42);
        ItemStack expected = reward.copy();
        hold(sponsor, reward);
        int before = TestSupport.countMarked(helper, manager, marker);

        BountyResult<Bounty> result = placeFromHand(manager, sponsor, target);

        helper.assertTrue(result.isOk(), "creation failed: " + result.error());
        helper.assertTrue(sponsor.getInventory().getItem(0).isEmpty(), "source slot not emptied");
        Bounty b = result.value();
        helper.assertTrue(ItemStack.matches(expected, b.reward()), "escrowed stack differs (item, components or count)");
        helper.assertTrue(b.reward() != reward, "escrow shares a reference with the inventory stack");
        helper.assertTrue(b.state() == BountyState.ACTIVE, "not active");
        helper.assertTrue(manager.findActive(b.id()).isPresent(), "not in saved data");
        assertConserved(helper, manager, marker, before);

        // Stack size too: a full stack of 64 moves as a whole.
        UUID marker2 = UUID.randomUUID();
        hold(sponsor, marked(Items.EMERALD, 64, marker2));
        Bounty b2 = placeFromHand(manager, sponsor, target).value();
        helper.assertTrue(b2 != null && b2.reward().getCount() == 64, "count not preserved");
        assertConserved(helper, manager, marker2, 64);
        helper.succeed();
    }

    @GameTest(template = T, batch = "create_refused")
    public static void createRefusedLeavesInventoryUnchanged(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        UUID marker = UUID.randomUUID();
        ItemStack held = marked(Items.DIAMOND, 7, marker);
        hold(sponsor, held);
        ItemStack snapshot = held.copy();

        // Target = self.
        expectRefused(helper, manager.create(sponsor, sponsor.getUUID(), sponsor.getInventory(), 0), BountyError.SELF_TARGET);
        // Unknown target (never connected).
        expectRefused(helper, manager.create(sponsor, UUID.randomUUID(), sponsor.getInventory(), 0), BountyError.UNKNOWN_TARGET);
        // Bad slot index.
        expectRefused(helper, manager.create(sponsor, target.getUUID(), sponsor.getInventory(), 999), BountyError.INVALID_SLOT);
        expectRefused(helper, manager.create(sponsor, target.getUUID(), sponsor.getInventory(), -1), BountyError.INVALID_SLOT);
        // Empty slot.
        expectRefused(helper, manager.create(sponsor, target.getUUID(), sponsor.getInventory(), 5), BountyError.EMPTY_SLOT);
        // Blacklisted item.
        BCConfig.REWARD_BLACKLIST.set(List.of("minecraft:diamond"));
        expectRefused(helper, manager.create(sponsor, target.getUUID(), sponsor.getInventory(), 0), BountyError.BLACKLISTED);
        BCConfig.REWARD_BLACKLIST.set(List.of());
        // Over-stacked (invalid quantity).
        sponsor.getInventory().setItem(1, marked(Items.ENDER_PEARL, 64, UUID.randomUUID()));
        expectRefused(helper, manager.create(sponsor, target.getUUID(), sponsor.getInventory(), 1), BountyError.INVALID_QUANTITY);
        sponsor.getInventory().setItem(1, ItemStack.EMPTY);

        helper.assertTrue(ItemStack.matches(snapshot, sponsor.getInventory().getItem(0)), "inventory changed by a refused creation");
        helper.assertTrue(manager.activeBounties().isEmpty(), "a refused creation created a bounty");
        assertConserved(helper, manager, marker, 7);
        helper.succeed();
    }

    @GameTest(template = T, batch = "create_limit")
    public static void createRefusedOverSponsorLimit(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        BCConfig.MAX_ACTIVE_BOUNTIES_PER_SPONSOR.set(1);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 1, marker));
        helper.assertTrue(placeFromHand(manager, sponsor, target).isOk(), "first creation should succeed");
        hold(sponsor, marked(Items.DIAMOND, 1, marker));
        expectRefused(helper, placeFromHand(manager, sponsor, target), BountyError.LIMIT_REACHED);
        helper.assertTrue(sponsor.getInventory().getItem(0).getCount() == 1, "inventory changed by a refused creation");
        assertConserved(helper, manager, marker, 2);
        helper.succeed();
    }

    @GameTest(template = T, batch = "cancel_refund")
    public static void cancelRefundsSponsorAndRecordsHistory(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        UUID marker = UUID.randomUUID();
        ItemStack reward = marked(Items.GOLDEN_APPLE, 5, marker);
        ItemStack expected = reward.copy();
        hold(sponsor, reward);
        Bounty b = placeFromHand(manager, sponsor, target).value();

        BountyResult<Bounty> result = manager.cancel(sponsor.getUUID(), b.id(), false);

        helper.assertTrue(result.isOk(), "cancel failed: " + result.error());
        helper.assertTrue(manager.findActive(b.id()).isEmpty(), "still active");
        Bounty closed = manager.findAny(b.id()).orElseThrow();
        helper.assertTrue(closed.state() == BountyState.CANCELLED, "not cancelled");
        helper.assertTrue(manager.history().stream().anyMatch(h -> h.id().equals(b.id())), "not in history");
        helper.assertTrue(closed.reward().isEmpty(), "history keeps an item");
        helper.assertTrue(manager.data().deliveries().isEmpty(), "refund not delivered to an online sponsor");
        helper.assertTrue(ItemStack.matches(expected, sponsor.getInventory().getItem(0)), "sponsor did not get the exact stack back");
        assertConserved(helper, manager, marker, 5);
        helper.succeed();
    }

    @GameTest(template = T, batch = "cancel_twice")
    public static void doubleCancelIsRefused(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 3, marker));
        Bounty b = placeFromHand(manager, sponsor, target).value();

        helper.assertTrue(manager.cancel(sponsor.getUUID(), b.id(), false).isOk(), "first cancel failed");
        expectRefused(helper, manager.cancel(sponsor.getUUID(), b.id(), false), BountyError.NOT_ACTIVE);
        expectRefused(helper, manager.cancel(sponsor.getUUID(), b.id(), true), BountyError.NOT_ACTIVE);
        expectRefused(helper, manager.cancel(sponsor.getUUID(), UUID.randomUUID(), false), BountyError.NOT_FOUND);

        assertConserved(helper, manager, marker, 3);
        helper.assertTrue(TestSupport.countInInventory(sponsor, marker) == 3, "sponsor should hold exactly 3");
        helper.succeed();
    }

    @GameTest(template = T, batch = "cancel_owner")
    public static void onlySponsorOrOperatorCanCancel(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer stranger = TestPlayers.hunter(helper);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 2, marker));
        Bounty b = placeFromHand(manager, sponsor, target).value();

        expectRefused(helper, manager.cancel(stranger.getUUID(), b.id(), false), BountyError.NOT_OWNER);
        expectRefused(helper, manager.cancel(target.getUUID(), b.id(), false), BountyError.NOT_OWNER);
        helper.assertTrue(manager.findActive(b.id()).isPresent(), "refused cancel changed state");

        helper.assertTrue(manager.cancel(stranger.getUUID(), b.id(), true).isOk(), "operator cancel failed");
        helper.assertTrue(TestSupport.countInInventory(sponsor, marker) == 2, "refund must go to the sponsor, not the operator");
        assertConserved(helper, manager, marker, 2);
        helper.succeed();
    }

    @GameTest(template = T, batch = "cancel_lock", timeoutTicks = 100)
    public static void cancelLockedAfterEligibleDamage(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        BCConfig.CANCEL_LOCK_SECONDS.set(1);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 1, marker));
        Bounty b = placeFromHand(manager, sponsor, target).value();

        // The sponsor's own hits do not lock (they are not eligible for their own bounty).
        hit(target, helper.getLevel().damageSources().playerAttack(sponsor), 1f);
        helper.assertTrue(!manager.isCombatLocked(b), "sponsor damage must not lock the cancel");

        hit(target, helper.getLevel().damageSources().playerAttack(hunter), 1f);
        expectRefused(helper, manager.cancel(sponsor.getUUID(), b.id(), false), BountyError.COMBAT_LOCKED);
        helper.assertTrue(manager.findActive(b.id()).isPresent(), "locked cancel changed state");
        assertConserved(helper, manager, marker, 1);

        // Once the lock (1 s) is over, the cancel goes through.
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(manager.cancel(sponsor.getUUID(), b.id(), false).isOk(), "cancel still locked after the delay");
            assertConserved(helper, manager, marker, 1);
            helper.assertTrue(TestSupport.countInInventory(sponsor, marker) == 1, "refund missing");
            helper.succeed();
        });
    }

    @GameTest(template = T, batch = "cancel_offline_sponsor")
    public static void refundToOfflineSponsorWaitsForLogin(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 4, marker));
        Bounty b = placeFromHand(manager, sponsor, target).value();
        var profile = sponsor.getGameProfile();
        TestPlayers.logout(sponsor);
        helper.assertTrue(manager.findActive(b.id()).isPresent(), "sponsor logout must not affect the bounty");

        helper.assertTrue(manager.cancel(UUID.randomUUID(), b.id(), true).isOk(), "operator cancel failed");
        helper.assertTrue(manager.deliveriesFor(profile.getId()).size() == 1, "refund not held for the offline sponsor");
        assertConserved(helper, manager, marker, 4);

        ServerPlayer back = TestPlayers.login(helper, profile, new net.minecraft.world.phys.Vec3(1.5, 1, 1.5));
        helper.assertTrue(TestSupport.countInInventory(back, marker) == 4, "refund not delivered on login");
        helper.assertTrue(manager.deliveriesFor(profile.getId()).isEmpty(), "delivery not deleted");
        assertConserved(helper, manager, marker, 4);
        helper.succeed();
    }

    static void expectRefused(GameTestHelper helper, BountyResult<?> result, BountyError expected) {
        helper.assertTrue(!result.isOk() && result.error() == expected, "expected " + expected + " but got " + (result.isOk() ? "success" : result.error()));
    }
}
