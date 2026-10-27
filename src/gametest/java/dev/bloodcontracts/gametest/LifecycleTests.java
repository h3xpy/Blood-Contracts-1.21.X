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
        helper.assertTrue(ItemStack.matches(expected, b.rewards().get(0)), "escrowed stack differs (item, components or count)");
        helper.assertTrue(b.rewards().get(0) != reward, "escrow shares a reference with the inventory stack");
        helper.assertTrue(b.state() == BountyState.ACTIVE, "not active");
        helper.assertTrue(manager.findActive(b.id()).isPresent(), "not in saved data");
        assertConserved(helper, manager, marker, before);

        // Stack size too: a full stack of 64 moves as a whole.
        UUID marker2 = UUID.randomUUID();
        hold(sponsor, marked(Items.EMERALD, 64, marker2));
        // Another target: a sponsor has at most one bounty per target.
        ServerPlayer target2 = TestPlayers.create(helper, "target2", new net.minecraft.world.phys.Vec3(5.5, 1, 4.5));
        Bounty b2 = placeFromHand(manager, sponsor, target2).value();
        helper.assertTrue(b2 != null && b2.rewards().get(0).getCount() == 64, "count not preserved");
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
        ServerPlayer otherTarget = TestPlayers.create(helper, "target2", new net.minecraft.world.phys.Vec3(5.5, 1, 4.5));
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 1, marker));
        helper.assertTrue(placeFromHand(manager, sponsor, target).isOk(), "first creation should succeed");
        hold(sponsor, marked(Items.DIAMOND, 1, marker));
        expectRefused(helper, placeFromHand(manager, sponsor, otherTarget), BountyError.LIMIT_REACHED);
        helper.assertTrue(sponsor.getInventory().getItem(0).getCount() == 1, "inventory changed by a refused creation");
        assertConserved(helper, manager, marker, 2);
        helper.succeed();
    }

    @GameTest(template = T, batch = "create_pair")
    public static void oneBountyPerSponsorTargetPair(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer other = TestPlayers.hunter(helper);
        ServerPlayer target = TestPlayers.target(helper);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 1, marker));
        Bounty first = placeFromHand(manager, sponsor, target).value();
        hold(sponsor, marked(Items.DIAMOND, 1, marker));
        expectRefused(helper, placeFromHand(manager, sponsor, target), BountyError.PAIR_EXISTS);
        // Still refused while the first one is under cancel notice.
        helper.assertTrue(manager.requestCancel(sponsor.getUUID(), first.id()).isOk(), "cancel request failed");
        expectRefused(helper, placeFromHand(manager, sponsor, target), BountyError.PAIR_EXISTS);
        // Another sponsor on the same target is fine.
        hold(other, marked(Items.EMERALD, 1, marker));
        helper.assertTrue(placeFromHand(manager, other, target).isOk(), "two sponsors on one target must be accepted");
        assertConserved(helper, manager, marker, 3);
        helper.succeed();
    }

    @GameTest(template = T, batch = "cancel_notice")
    public static void cancelNoticeKeepsBountyActiveThenRefunds(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.TestClock clock = TestSupport.clock(manager);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        UUID marker = UUID.randomUUID();
        ItemStack reward = marked(Items.GOLDEN_APPLE, 5, marker);
        ItemStack expected = reward.copy();
        hold(sponsor, reward);
        Bounty b = placeFromHand(manager, sponsor, target).value();

        BountyResult<Bounty> result = manager.cancel(sponsor.getUUID(), b.id(), false);
        helper.assertTrue(result.isOk() && result.value().isCancelPending(), "cancel request failed: " + result.error());
        clock.advanceMinutes(29);
        manager.periodic();
        helper.assertTrue(manager.findActive(b.id()).isPresent(), "bounty must stay active during the notice");
        helper.assertTrue(manager.deliveriesFor(sponsor.getUUID()).isEmpty(), "refund before the end of the notice");

        clock.advanceMinutes(1);
        manager.periodic();
        Bounty closed = manager.findAny(b.id()).orElseThrow();
        helper.assertTrue(closed.state() == BountyState.CANCELLED, "not cancelled at the end of the notice");
        helper.assertTrue(manager.history().stream().anyMatch(h -> h.id().equals(b.id())), "not in history");
        helper.assertTrue(TestSupport.countOwed(manager, sponsor, marker) == 5, "refund not waiting to be claimed");
        manager.periodic();
        helper.assertTrue(TestSupport.countOwed(manager, sponsor, marker) == 5, "refund given twice");
        assertConserved(helper, manager, marker, 5);
        TestSupport.claimAll(helper, manager, sponsor);
        helper.assertTrue(ItemStack.matches(expected, sponsor.getInventory().getItem(0)), "sponsor did not get the exact stack back");
        assertConserved(helper, manager, marker, 5);
        helper.succeed();
    }

    @GameTest(template = T, batch = "cancel_twice")
    public static void secondCancelRequestDoesNotShortenTheNotice(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.TestClock clock = TestSupport.clock(manager);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 3, marker));
        Bounty b = placeFromHand(manager, sponsor, target).value();

        long end = manager.requestCancel(sponsor.getUUID(), b.id()).value().cancelAt();
        clock.advanceMinutes(10);
        helper.assertTrue(manager.requestCancel(sponsor.getUUID(), b.id()).value().cancelAt() == end, "second request moved the deadline");
        clock.advanceMinutes(20);
        manager.periodic();
        expectRefused(helper, manager.cancel(sponsor.getUUID(), b.id(), false), BountyError.NOT_ACTIVE);
        expectRefused(helper, manager.cancel(sponsor.getUUID(), b.id(), true), BountyError.NOT_ACTIVE);
        expectRefused(helper, manager.cancel(sponsor.getUUID(), UUID.randomUUID(), false), BountyError.NOT_FOUND);
        assertConserved(helper, manager, marker, 3);
        TestSupport.claimAll(helper, manager, sponsor);
        helper.assertTrue(TestSupport.countInInventory(sponsor, marker) == 3, "sponsor should hold exactly 3");
        helper.succeed();
    }

    @GameTest(template = T, batch = "cancel_resume")
    public static void resumeWithdrawsTheCancellation(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.TestClock clock = TestSupport.clock(manager);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer stranger = TestPlayers.hunter(helper);
        ServerPlayer target = TestPlayers.target(helper);
        hold(sponsor, new ItemStack(Items.DIAMOND));
        Bounty b = placeFromHand(manager, sponsor, target).value();
        expectRefused(helper, manager.resume(sponsor.getUUID(), b.id()), BountyError.NOT_CANCELLING);
        manager.requestCancel(sponsor.getUUID(), b.id());
        clock.advanceMinutes(15);
        expectRefused(helper, manager.resume(stranger.getUUID(), b.id()), BountyError.NOT_OWNER);
        BountyResult<Bounty> resumed = manager.resume(sponsor.getUUID(), b.id());
        helper.assertTrue(resumed.isOk() && !resumed.value().isCancelPending(), "resume failed: " + resumed.error());
        clock.advanceMinutes(31);
        manager.periodic();
        helper.assertTrue(manager.findActive(b.id()).isPresent(), "resumed bounty was cancelled anyway");
        helper.succeed();
    }

    @GameTest(template = T, batch = "cancel_kill_during_notice")
    public static void killDuringNoticePaysNormally(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.TestClock clock = TestSupport.clock(manager);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 2, marker));
        Bounty b = placeFromHand(manager, sponsor, target).value();
        manager.requestCancel(sponsor.getUUID(), b.id());
        clock.advanceMinutes(20);

        hit(target, target.damageSources().playerAttack(hunter), 1000f);

        Bounty closed = manager.findAny(b.id()).orElseThrow();
        helper.assertTrue(closed.state() == BountyState.CLAIMED && hunter.getUUID().equals(closed.claimedBy()), "not paid during the notice");
        helper.assertTrue(TestSupport.countOwed(manager, hunter, marker) == 2, "hunter not paid");
        helper.assertTrue(manager.deliveriesFor(sponsor.getUUID()).isEmpty(), "sponsor refunded although the bounty was collected");
        helper.assertTrue(manager.heartBalanceHp(target.getUUID()) == -2 && manager.heartBalanceHp(sponsor.getUUID()) == 2, "heart not moved");
        helper.assertTrue(manager.immuneUntil(target.getUUID()).isPresent(), "target not immune");
        clock.advanceMinutes(20);
        manager.periodic();
        helper.assertTrue(manager.deliveriesFor(sponsor.getUUID()).isEmpty(), "notice end refunded a collected bounty");
        assertConserved(helper, manager, marker, 2);
        helper.succeed();
    }

    @GameTest(template = T, batch = "cancel_notice_restart")
    public static void noticeEndedDuringDowntimeIsFinalizedAtStart(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.TestClock clock = TestSupport.clock(manager);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 1, marker));
        Bounty b = placeFromHand(manager, sponsor, target).value();
        manager.requestCancel(sponsor.getUUID(), b.id());
        // The server is down for 45 minutes: the restart finalizes the notice.
        clock.advanceMinutes(45);
        manager.onServerStarted();
        helper.assertTrue(manager.findAny(b.id()).orElseThrow().state() == BountyState.CANCELLED, "not finalized at start");
        helper.assertTrue(TestSupport.countOwed(manager, sponsor, marker) == 1, "refund missing");
        assertConserved(helper, manager, marker, 1);
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
        helper.assertTrue(TestSupport.countOwed(manager, sponsor, marker) == 2, "refund must go to the sponsor, not the operator");
        helper.assertTrue(manager.deliveriesFor(stranger.getUUID()).isEmpty(), "operator must not be owed anything");
        assertConserved(helper, manager, marker, 2);
        helper.succeed();
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
        helper.assertTrue(TestSupport.countOwed(manager, back, marker) == 4, "refund lost across the reconnection");
        TestSupport.claimAll(helper, manager, back);
        helper.assertTrue(TestSupport.countInInventory(back, marker) == 4, "refund not handed over on claim");
        helper.assertTrue(manager.deliveriesFor(profile.getId()).isEmpty(), "delivery not deleted");
        assertConserved(helper, manager, marker, 4);
        helper.succeed();
    }

    static void expectRefused(GameTestHelper helper, BountyResult<?> result, BountyError expected) {
        helper.assertTrue(!result.isOk() && result.error() == expected, "expected " + expected + " but got " + (result.isOk() ? "success" : result.error()));
    }
}
