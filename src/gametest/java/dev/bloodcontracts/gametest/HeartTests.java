package dev.bloodcontracts.gametest;

import static dev.bloodcontracts.gametest.TestSupport.hold;
import static dev.bloodcontracts.gametest.TestSupport.marked;
import static dev.bloodcontracts.gametest.TestSupport.placeFromHand;

import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.config.BCConfig;
import dev.bloodcontracts.core.Bounty;
import dev.bloodcontracts.core.BountyManager;
import dev.bloodcontracts.core.BountySavedData;
import dev.bloodcontracts.core.BountyState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Heart transfers, bounds, progressive regeneration, immunity. Real time runs on a {@link TestSupport.TestClock}. */
@GameTestHolder(BloodContracts.MODID)
@PrefixGameTestTemplate(false)
public class HeartTests {
    private static final String T = BloodContractsGameTests.TEMPLATE;

    private static Bounty place(BountyManager manager, ServerPlayer sponsor, ServerPlayer target) {
        hold(sponsor, new ItemStack(Items.DIAMOND));
        Bounty b = placeFromHand(manager, sponsor, target).value();
        if (b == null) {
            throw new IllegalStateException("setup: bounty not created");
        }
        return b;
    }

    /** The death handler, as LivingDeathEvent calls it (works on a mock player that already died). */
    private static List<Bounty> kill(BountyManager manager, ServerPlayer target, ServerPlayer hunter) {
        return manager.onDeath(target, target.damageSources().playerAttack(hunter));
    }

    private static int bal(BountyManager m, ServerPlayer p) {
        return m.heartBalanceHp(p.getUUID());
    }

    private static void assertSumZero(GameTestHelper helper, BountyManager manager, ServerPlayer... players) {
        int sum = 0;
        for (ServerPlayer p : players) {
            sum += bal(manager, p);
        }
        helper.assertTrue(sum == 0, "balances do not sum to 0: " + sum);
        helper.assertTrue(manager.data().transfers().stream().allMatch(t -> t.remainingHp() > 0), "empty transfer kept");
    }

    private static double maxHealth(ServerPlayer p) {
        return p.getAttributeValue(Attributes.MAX_HEALTH);
    }

    // ---------------------------------------------------------------- transfer and bounds

    @GameTest(template = T, batch = "heart_transfer")
    public static void collectedBountyMovesOneHeart(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.clock(manager);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        place(manager, sponsor, target);
        kill(manager, target, hunter);
        helper.assertTrue(bal(manager, target) == -2 && bal(manager, sponsor) == 2 && bal(manager, hunter) == 0, "heart not moved to the sponsor");
        helper.assertTrue(maxHealth(target) == 18 && maxHealth(sponsor) == 22, "MAX_HEALTH not applied: " + maxHealth(target) + "/" + maxHealth(sponsor));
        helper.assertTrue(target.getAttribute(Attributes.MAX_HEALTH).getModifier(BountyManager.HEART_MODIFIER) != null, "modifier id wrong");
        assertSumZero(helper, manager, sponsor, target, hunter);
        helper.succeed();
    }

    @GameTest(template = T, batch = "heart_three_bounties")
    public static void severalBountiesStillOneHeartToTheOldestSponsorWithRoom(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.TestClock clock = TestSupport.clock(manager);
        BCConfig.TARGET_IMMUNITY_MINUTES.set(0);
        BCConfig.MAX_HEARTS_GAINED.set(1);
        ServerPlayer s1 = TestPlayers.sponsor(helper);
        ServerPlayer s2 = TestPlayers.create(helper, "sponsor2", new Vec3(1.5, 1, 4.5));
        ServerPlayer s3 = TestPlayers.create(helper, "sponsor3", new Vec3(1.5, 1, 6.5));
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer other = TestPlayers.create(helper, "victim2", new Vec3(5.5, 1, 6.5));
        ServerPlayer hunter = TestPlayers.hunter(helper);
        // s1 first reaches its ceiling (+1 heart) on another target.
        place(manager, s1, other);
        kill(manager, other, hunter);
        helper.assertTrue(bal(manager, s1) == 2, "setup: s1 not at its ceiling");
        clock.advanceMinutes(1);
        place(manager, s1, target);
        clock.advanceMinutes(1);
        place(manager, s2, target);
        clock.advanceMinutes(1);
        place(manager, s3, target);

        List<Bounty> claimed = kill(manager, target, hunter);

        helper.assertTrue(claimed.size() == 3, "all three bounties must be collected, got " + claimed.size());
        helper.assertTrue(claimed.get(0).sponsor().equals(s1.getUUID()) && claimed.get(2).sponsor().equals(s3.getUUID()), "not in creation order");
        helper.assertTrue(bal(manager, target) == -2, "target must lose exactly one heart, lost " + -bal(manager, target));
        helper.assertTrue(bal(manager, s1) == 2 && bal(manager, s2) == 2 && bal(manager, s3) == 0, "heart must skip the capped oldest sponsor and go to the next");
        assertSumZero(helper, manager, s1, s2, s3, target, other, hunter);
        helper.succeed();
    }

    @GameTest(template = T, batch = "heart_floor")
    public static void floorStopsTransfersButItemsArePaid(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.clock(manager);
        BCConfig.TARGET_IMMUNITY_MINUTES.set(0);
        BCConfig.MAX_HEARTS_LOST.set(1);
        ServerPlayer s1 = TestPlayers.sponsor(helper);
        ServerPlayer s2 = TestPlayers.create(helper, "sponsor2", new Vec3(1.5, 1, 4.5));
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        place(manager, s1, target);
        kill(manager, target, hunter);
        Bounty second = place(manager, s2, target);
        kill(manager, target, hunter);
        helper.assertTrue(bal(manager, target) == -2, "target went below the floor: " + bal(manager, target));
        helper.assertTrue(bal(manager, s2) == 0, "a heart nobody lost was received");
        helper.assertTrue(manager.findAny(second.id()).orElseThrow().state() == BountyState.CLAIMED
                && manager.deliveriesFor(hunter.getUUID()).size() == 2, "item not paid at the floor");
        assertSumZero(helper, manager, s1, s2, target, hunter);
        helper.succeed();
    }

    @GameTest(template = T, batch = "heart_ceiling")
    public static void ceilingStopsTransfers(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.clock(manager);
        BCConfig.MAX_HEARTS_GAINED.set(1);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer t1 = TestPlayers.target(helper);
        ServerPlayer t2 = TestPlayers.create(helper, "target2", new Vec3(5.5, 1, 4.5));
        ServerPlayer hunter = TestPlayers.hunter(helper);
        place(manager, sponsor, t1);
        kill(manager, t1, hunter);
        place(manager, sponsor, t2);
        kill(manager, t2, hunter);
        helper.assertTrue(bal(manager, sponsor) == 2 && bal(manager, t2) == 0, "transfer beyond the ceiling");
        helper.assertTrue(manager.deliveriesFor(hunter.getUUID()).size() == 2, "item not paid at the ceiling");
        assertSumZero(helper, manager, sponsor, t1, t2, hunter);
        helper.succeed();
    }

    @GameTest(template = T, batch = "heart_no_kill")
    public static void loweringMaxHealthNeverKills(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.clock(manager);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        place(manager, sponsor, target);
        manager.claimAll(target.getUUID(), hunter.getUUID());
        helper.assertTrue(target.isAlive() && target.getHealth() <= target.getMaxHealth() && target.getHealth() > 0, "lowering max health hurt the player");
        helper.assertTrue(target.getHealth() == 18, "health not lowered to the new maximum: " + target.getHealth());
        helper.succeed();
    }

    // ---------------------------------------------------------------- regeneration

    @GameTest(template = T, batch = "regen_online")
    public static void onlineRegenOneHpPerNinetyMinutes(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.TestClock clock = TestSupport.clock(manager);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        place(manager, sponsor, target);
        kill(manager, target, hunter);
        clock.advanceMinutes(89);
        manager.periodic();
        helper.assertTrue(bal(manager, target) == -2, "regen too early");
        clock.advanceMinutes(1);
        manager.periodic();
        helper.assertTrue(bal(manager, target) == -1 && bal(manager, sponsor) == 1, "1 HP not returned after 90 min online");
        helper.assertTrue(maxHealth(target) == 19 && maxHealth(sponsor) == 21, "modifiers not updated after regen");
        clock.advanceMinutes(90);
        manager.periodic();
        helper.assertTrue(bal(manager, target) == 0 && bal(manager, sponsor) == 0, "1 heart not back after 3 h online");
        helper.assertTrue(manager.data().regen(target.getUUID()).isEmpty(), "gauge kept after full repayment");
        helper.assertTrue(manager.data().transfers().isEmpty(), "transfer kept at 0 HP");
        helper.succeed();
    }

    @GameTest(template = T, batch = "regen_offline")
    public static void offlineRegenOneHpPerTwelveHours(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.TestClock clock = TestSupport.clock(manager);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        place(manager, sponsor, target);
        kill(manager, target, hunter);
        var profile = target.getGameProfile();
        TestPlayers.logout(target);
        clock.advanceMinutes(11 * 60 + 59);
        ServerPlayer back = TestPlayers.login(helper, profile, new Vec3(5.5, 1, 1.5));
        helper.assertTrue(manager.heartBalanceHp(back.getUUID()) == -2, "regen before 12 h offline");
        TestPlayers.logout(back);
        clock.advanceMinutes(1);
        ServerPlayer again = TestPlayers.login(helper, profile, new Vec3(5.5, 1, 1.5));
        helper.assertTrue(manager.heartBalanceHp(again.getUUID()) == -1, "1 HP not returned after 12 h offline");
        helper.assertTrue(maxHealth(again) == 19, "modifier not applied at login");
        helper.succeed();
    }

    @GameTest(template = T, batch = "regen_offline_30h")
    public static void thirtyHoursOfflineGiveTwoHpAndKeepHalf(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.TestClock clock = TestSupport.clock(manager);
        BCConfig.TARGET_IMMUNITY_MINUTES.set(0);
        ServerPlayer s1 = TestPlayers.sponsor(helper);
        ServerPlayer s2 = TestPlayers.create(helper, "sponsor2", new Vec3(1.5, 1, 4.5));
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        place(manager, s1, target);
        kill(manager, target, hunter);
        place(manager, s2, target);
        kill(manager, target, hunter);
        helper.assertTrue(bal(manager, target) == -4, "setup: expected -2 hearts");
        var profile = target.getGameProfile();
        TestPlayers.logout(target);
        clock.advanceMinutes(30 * 60);
        ServerPlayer back = TestPlayers.login(helper, profile, new Vec3(5.5, 1, 1.5));
        helper.assertTrue(manager.heartBalanceHp(back.getUUID()) == -2, "30 h offline must give back exactly 2 HP, balance " + manager.heartBalanceHp(back.getUUID()));
        double progress = manager.data().regen(back.getUUID()).orElseThrow().progress();
        helper.assertTrue(Math.abs(progress - 0.5) < 1e-6, "gauge must keep 0.5, has " + progress);
        // Oldest transfer first: s1 was paid back before s2.
        helper.assertTrue(bal(manager, s1) == 0 && bal(manager, s2) == 2, "transfers not repaid oldest first");
        helper.succeed();
    }

    @GameTest(template = T, batch = "regen_mixed")
    public static void mixedOnlineAndOfflineTime(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.TestClock clock = TestSupport.clock(manager);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        place(manager, sponsor, target);
        kill(manager, target, hunter);
        clock.advanceMinutes(45);
        var profile = target.getGameProfile();
        TestPlayers.logout(target);
        helper.assertTrue(Math.abs(manager.data().regen(profile.getId()).orElseThrow().progress() - 0.5) < 1e-6, "45 min online must fill half the gauge");
        clock.advanceMinutes(6 * 60);
        ServerPlayer back = TestPlayers.login(helper, profile, new Vec3(5.5, 1, 1.5));
        helper.assertTrue(manager.heartBalanceHp(back.getUUID()) == -1, "1 HP not returned after 45 min online + 6 h offline");
        helper.assertTrue(Math.abs(manager.data().regen(back.getUUID()).orElseThrow().progress()) < 1e-6, "gauge not back to 0");
        helper.succeed();
    }

    @GameTest(template = T, batch = "regen_downtime")
    public static void serverDowntimeCountsAsOffline(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.TestClock clock = TestSupport.clock(manager);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        place(manager, sponsor, target);
        kill(manager, target, hunter);
        manager.onServerStopping();
        // 12 h of downtime: at the offline rate, exactly 1 HP (at the online rate it would have been 8).
        clock.advanceMinutes(12 * 60);
        manager.onServerStarted();
        helper.assertTrue(bal(manager, target) == -1, "downtime must count as offline time, balance " + bal(manager, target));
        helper.succeed();
    }

    @GameTest(template = T, batch = "regen_no_reset")
    public static void newLossDoesNotResetTheGauge(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.TestClock clock = TestSupport.clock(manager);
        BCConfig.TARGET_IMMUNITY_MINUTES.set(0);
        ServerPlayer s1 = TestPlayers.sponsor(helper);
        ServerPlayer s2 = TestPlayers.create(helper, "sponsor2", new Vec3(1.5, 1, 4.5));
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        place(manager, s1, target);
        kill(manager, target, hunter);
        clock.advanceMinutes(45);
        place(manager, s2, target);
        kill(manager, target, hunter);
        helper.assertTrue(Math.abs(manager.data().regen(target.getUUID()).orElseThrow().progress() - 0.5) < 1e-6, "a new loss reset the gauge");
        clock.advanceMinutes(45);
        manager.periodic();
        helper.assertTrue(bal(manager, target) == -3 && bal(manager, s1) == 1 && bal(manager, s2) == 2, "regen after 90 min total must repay the oldest transfer");
        helper.succeed();
    }

    @GameTest(template = T, batch = "regen_permanent")
    public static void permanentModeNeverGivesBack(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.TestClock clock = TestSupport.clock(manager);
        BCConfig.HEART_REGEN_MODE.set(BCConfig.HeartRegenMode.PERMANENT);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        place(manager, sponsor, target);
        kill(manager, target, hunter);
        clock.advanceMinutes(48 * 60);
        manager.periodic();
        manager.onServerStarted();
        helper.assertTrue(bal(manager, target) == -2, "permanent mode gave hearts back");
        helper.succeed();
    }

    @GameTest(template = T, batch = "regen_coherence", timeoutTicks = 200)
    public static void killedAtEveryImmunityEndThenRecovers(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.TestClock clock = TestSupport.clock(manager);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        List<ServerPlayer> sponsors = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            sponsors.add(TestPlayers.create(helper, "spons" + i, new Vec3(1.5, 1, 1.5 + i)));
        }
        List<ServerPlayer> everyone = new ArrayList<>(sponsors);
        everyone.add(target);
        everyone.add(hunter);
        int lowest = 0;
        // The target stays online and is killed under contract each time its immunity ends.
        for (int cycle = 0; cycle < 30; cycle++) {
            ServerPlayer sponsor = sponsors.get(cycle % sponsors.size());
            place(manager, sponsor, target);
            List<Bounty> claimed = kill(manager, target, hunter);
            helper.assertTrue(claimed.size() == 1, "cycle " + cycle + ": bounty not collected");
            assertSumZero(helper, manager, everyone.toArray(ServerPlayer[]::new));
            lowest = Math.min(lowest, bal(manager, target));
            clock.advanceMinutes(120);
            manager.periodic();
        }
        helper.assertTrue(lowest == -10, "losses must outpace online regen down to the floor, lowest " + lowest);
        // Then 15 h online without dying: everything comes back.
        for (int i = 0; i < 10; i++) {
            clock.advanceMinutes(90);
            manager.periodic();
        }
        helper.assertTrue(bal(manager, target) == 0, "not fully recovered after 15 h online: " + bal(manager, target));
        assertSumZero(helper, manager, everyone.toArray(ServerPlayer[]::new));
        helper.succeed();
    }

    // ---------------------------------------------------------------- modifier lifecycle

    @GameTest(template = T, batch = "heart_modifier_lifecycle")
    public static void modifierReappliedWithoutDuplicates(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.clock(manager);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        place(manager, sponsor, target);
        manager.claimAll(target.getUUID(), hunter.getUUID());

        // Respawn (new player object, Clone and Respawn events).
        ServerPlayer respawned = helper.getLevel().getServer().getPlayerList().respawn(target, false, Entity.RemovalReason.KILLED);
        helper.assertTrue(maxHealth(respawned) == 18, "modifier lost on respawn: " + maxHealth(respawned));
        // Reconnection.
        var profile = respawned.getGameProfile();
        TestPlayers.logout(respawned);
        ServerPlayer back = TestPlayers.login(helper, profile, new Vec3(5.5, 1, 1.5));
        helper.assertTrue(maxHealth(back) == 18, "modifier lost on reconnection");
        // Dimension change.
        var nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        back.teleportTo(nether, 0, 100, 0, 0, 0);
        back.hasChangedDimension();
        helper.assertTrue(maxHealth(back) == 18, "modifier lost on dimension change");
        manager.applyHeartModifier(back);
        manager.applyHeartModifier(back);
        long ours = back.getAttribute(Attributes.MAX_HEALTH).getModifiers().stream().filter(m -> m.id().equals(BountyManager.HEART_MODIFIER)).count();
        helper.assertTrue(ours == 1 && maxHealth(back) == 18, "duplicate modifier");
        helper.succeed();
    }

    // ---------------------------------------------------------------- immunity

    @GameTest(template = T, batch = "immunity")
    public static void immunityBlocksCollectionUntilItEnds(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.TestClock clock = TestSupport.clock(manager);
        ServerPlayer s1 = TestPlayers.sponsor(helper);
        ServerPlayer s2 = TestPlayers.create(helper, "sponsor2", new Vec3(1.5, 1, 4.5));
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        place(manager, s1, target);
        kill(manager, target, hunter);
        helper.assertTrue(manager.immuneUntil(target.getUUID()).isPresent(), "not immune after a collected bounty");
        // A bounty can still be placed on an immune target, but not collected.
        Bounty during = place(manager, s2, target);
        clock.advanceMinutes(119);
        kill(manager, target, hunter);
        helper.assertTrue(manager.findActive(during.id()).isPresent(), "collected during immunity");
        helper.assertTrue(bal(manager, target) == -2 + regenDuring(manager, target), "heart moved during immunity");
        // Immunity is real time: it runs out while the target is offline.
        var profile = target.getGameProfile();
        TestPlayers.logout(target);
        clock.advanceMinutes(2);
        helper.assertTrue(manager.immuneUntil(profile.getId()).isEmpty(), "immunity did not expire while offline");
        ServerPlayer back = TestPlayers.login(helper, profile, new Vec3(5.5, 1, 1.5));
        kill(manager, back, hunter);
        helper.assertTrue(manager.findAny(during.id()).orElseThrow().state() == BountyState.CLAIMED, "not collectable after immunity");
        helper.succeed();
    }

    private static int regenDuring(BountyManager manager, ServerPlayer target) {
        // 119 minutes online returned 1 HP (90-minute step) on the first loss.
        return manager.heartBalanceHp(target.getUUID()) + 2;
    }

    @GameTest(template = T, batch = "heart_persistence")
    public static void transfersAndImmunitySurviveReload(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.clock(manager);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        place(manager, sponsor, target);
        kill(manager, target, hunter);
        var registries = helper.getLevel().registryAccess();
        BountySavedData reloaded = BountySavedData.load(manager.data().save(new net.minecraft.nbt.CompoundTag(), registries), registries);
        helper.assertTrue(reloaded.balance(target.getUUID()) == -2 && reloaded.balance(sponsor.getUUID()) == 2, "transfers lost");
        helper.assertTrue(reloaded.immuneUntil(target.getUUID()).equals(manager.data().immuneUntil(target.getUUID())), "immunity lost");
        helper.assertTrue(reloaded.regen(target.getUUID()).isPresent(), "gauge lost");
        helper.assertTrue(reloaded.pairCount(hunter.getUUID(), target.getUUID()) == 1, "pair counter lost");
        helper.succeed();
    }

    @GameTest(template = T, batch = "heart_config_warnings")
    public static void inconsistentSettingsAreWarned(GameTestHelper helper) {
        TestSupport.reset(helper);
        helper.assertTrue(BountyManager.configWarnings().isEmpty(), "default settings must be consistent");
        BCConfig.HEART_REGEN_ONLINE_STEP_MINUTES.set(60);
        helper.assertTrue(BountyManager.configWarnings().size() == 1, "online x 2 <= immunity not warned");
        BCConfig.HEART_REGEN_ONLINE_STEP_MINUTES.set(90);
        BCConfig.HEART_REGEN_OFFLINE_STEP_MINUTES.set(30);
        helper.assertTrue(BountyManager.configWarnings().size() == 1, "offline < online not warned");
        TestSupport.reset(helper);
        helper.succeed();
    }
}
