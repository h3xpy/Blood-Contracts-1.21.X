package dev.bloodcontracts.gametest;

import static dev.bloodcontracts.gametest.TestSupport.assertConserved;
import static dev.bloodcontracts.gametest.TestSupport.countInInventory;
import static dev.bloodcontracts.gametest.TestSupport.hit;
import static dev.bloodcontracts.gametest.TestSupport.hold;
import static dev.bloodcontracts.gametest.TestSupport.marked;
import static dev.bloodcontracts.gametest.TestSupport.placeFromHand;

import com.mojang.authlib.GameProfile;
import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.core.Bounty;
import dev.bloodcontracts.core.BountyManager;
import dev.bloodcontracts.core.BountySavedData;
import dev.bloodcontracts.core.BountyState;
import dev.bloodcontracts.core.PendingDelivery;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Anti-abuse rules, replays, offline players, full inventories and persistence. */
@GameTestHolder(BloodContracts.MODID)
@PrefixGameTestTemplate(false)
public class RobustnessTests {
    private static final String T = BloodContractsGameTests.TEMPLATE;

    @GameTest(template = T, batch = "sponsor_kills")
    public static void sponsorKillingTargetKeepsOwnBountyActive(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 2, marker));
        Bounty b = placeFromHand(manager, sponsor, target).value();

        hit(target, target.damageSources().playerAttack(sponsor), 1000f);

        helper.assertTrue(target.isDeadOrDying(), "target did not die");
        helper.assertTrue(manager.findActive(b.id()).isPresent(), "sponsor claimed their own bounty");
        helper.assertTrue(countInInventory(sponsor, marker) == 0, "sponsor got the item back by killing");
        assertConserved(helper, manager, marker, 2);
        helper.succeed();
    }

    @GameTest(template = T, batch = "multi_bounties")
    public static void severalBountiesAllPaidToEligibleHunter(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsorA = TestPlayers.sponsor(helper);
        ServerPlayer sponsorB = TestPlayers.create(helper, "sponsorB", new Vec3(1.5, 1, 4.5));
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        UUID mA = UUID.randomUUID(), mB = UUID.randomUUID(), mH = UUID.randomUUID();
        hold(sponsorA, marked(Items.DIAMOND, 1, mA));
        hold(sponsorB, marked(Items.EMERALD, 2, mB));
        hold(hunter, marked(Items.GOLD_INGOT, 3, mH));
        Bounty a = placeFromHand(manager, sponsorA, target).value();
        Bounty bb = placeFromHand(manager, sponsorB, target).value();
        Bounty own = placeFromHand(manager, hunter, target).value();

        hit(target, target.damageSources().playerAttack(hunter), 1000f);

        helper.assertTrue(manager.findAny(a.id()).orElseThrow().state() == BountyState.CLAIMED, "A not claimed");
        helper.assertTrue(manager.findAny(bb.id()).orElseThrow().state() == BountyState.CLAIMED, "B not claimed");
        helper.assertTrue(manager.findActive(own.id()).isPresent(), "hunter's own bounty must stay active");
        TestSupport.claimAll(helper, manager, hunter);
        helper.assertTrue(countInInventory(hunter, mA) == 1 && countInInventory(hunter, mB) == 2, "hunter not paid both rewards");
        helper.assertTrue(countInInventory(hunter, mH) == 0, "hunter must not recover their own reward");
        assertConserved(helper, manager, mA, 1);
        assertConserved(helper, manager, mB, 2);
        assertConserved(helper, manager, mH, 3);
        helper.succeed();
    }

    @GameTest(template = T, batch = "death_replay")
    public static void replayedDeathEventPaysOnce(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 3, marker));
        placeFromHand(manager, sponsor, target);

        DamageSource source = target.damageSources().playerAttack(hunter);
        hit(target, source, 1000f);
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(target, source));
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(target, source));
        TestSupport.claimAll(helper, manager, hunter);

        helper.assertTrue(countInInventory(hunter, marker) == 3, "hunter should be paid exactly once");
        helper.assertTrue(manager.history().size() == 1, "history should hold one claim");
        assertConserved(helper, manager, marker, 3);
        helper.succeed();
    }

    @GameTest(template = T, batch = "hunter_offline", timeoutTicks = 100)
    public static void offlineHunterIsPaidOnceOnReconnect(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 3, marker));
        Bounty b = placeFromHand(manager, sponsor, target).value();
        GameProfile hunterProfile = hunter.getGameProfile();

        // The hunter knocks the target off, then disconnects before the target hits the void.
        hit(target, target.damageSources().playerAttack(hunter), 1f);
        TestPlayers.logout(hunter);
        hit(target, target.damageSources().fellOutOfWorld(), 1000f);

        helper.assertTrue(manager.findAny(b.id()).orElseThrow().state() == BountyState.CLAIMED, "not claimed");
        helper.assertTrue(manager.deliveriesFor(hunterProfile.getId()).size() == 1, "delivery not kept for the offline hunter");
        assertConserved(helper, manager, marker, 3);

        helper.runAfterDelay(5, () -> {
            ServerPlayer back = TestPlayers.login(helper, hunterProfile, new Vec3(3.5, 1, 1.5));
            helper.assertTrue(TestSupport.countOwed(manager, back, marker) == 3, "reward lost across the reconnection");
            helper.assertTrue(countInInventory(back, marker) == 0, "reward pushed into the inventory without a claim");
            TestSupport.claimAll(helper, manager, back);
            helper.assertTrue(countInInventory(back, marker) == 3, "not handed over on claim");
            helper.assertTrue(manager.deliveriesFor(hunterProfile.getId()).isEmpty(), "delivery not deleted");
            // Claiming again, or another login, must not hand anything over twice.
            manager.onPlayerLogin(back);
            var again = manager.claimRewards(back, java.util.Optional.empty());
            helper.assertTrue(!again.isOk() && again.error() == dev.bloodcontracts.core.BountyError.NOTHING_TO_CLAIM, "second claim should be refused");
            helper.assertTrue(countInInventory(back, marker) == 3, "delivered twice");
            assertConserved(helper, manager, marker, 3);
            helper.succeed();
        });
    }

    @GameTest(template = T, batch = "target_offline")
    public static void targetLogoutKeepsBountyActive(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 1, marker));
        Bounty b = placeFromHand(manager, sponsor, target).value();
        TestPlayers.logout(target);
        helper.assertTrue(manager.findActive(b.id()).isPresent(), "target logout changed the bounty");
        // A bounty can still be placed on a known but offline target.
        ServerPlayer sponsor2 = TestPlayers.create(helper, "sponsor2", new Vec3(1.5, 1, 4.5));
        hold(sponsor2, marked(Items.DIAMOND, 1, marker));
        helper.assertTrue(manager.create(sponsor2, target.getUUID(), sponsor2.getInventory(), 0).isOk(), "offline known target refused");
        assertConserved(helper, manager, marker, 2);
        helper.succeed();
    }

    @GameTest(template = T, batch = "full_inventory")
    public static void fullInventoryDropsRewardAtFeet(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 3, marker));
        placeFromHand(manager, sponsor, target);
        for (int i = 0; i < hunter.getInventory().items.size(); i++) {
            hunter.getInventory().items.set(i, new ItemStack(Items.COBBLESTONE, 64));
        }

        hit(target, target.damageSources().playerAttack(hunter), 1000f);
        TestSupport.claimAll(helper, manager, hunter);

        helper.assertTrue(countInInventory(hunter, marker) == 0, "item fit in a full inventory?");
        helper.assertTrue(manager.data().deliveries().isEmpty(), "delivery not completed");
        var drops = helper.getLevel().getEntities(EntityType.ITEM, hunter.getBoundingBox().inflate(2),
                e -> TestSupport.isMarked(e.getItem(), marker));
        helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().getCount() == 3, "reward not dropped at the hunter's feet");
        ItemEntity drop = drops.get(0);
        helper.assertTrue(hunter.getUUID().equals(drop.getTarget()), "drop should be reserved for the hunter");
        assertConserved(helper, manager, marker, 3);
        helper.succeed();
    }

    @GameTest(template = T, batch = "creative_overflow")
    public static void creativeHunterOverflowIsNotVoided(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 3, marker));
        placeFromHand(manager, sponsor, target);
        for (int i = 0; i < hunter.getInventory().items.size(); i++) {
            hunter.getInventory().items.set(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        hunter.setGameMode(net.minecraft.world.level.GameType.CREATIVE);

        manager.claimAll(target.getUUID(), hunter.getUUID());
        TestSupport.claimAll(helper, manager, hunter);

        assertConserved(helper, manager, marker, 3);
        helper.succeed();
    }

    @GameTest(template = T, batch = "persistence")
    public static void bountiesHistoryAndDeliveriesSurviveRestart(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 3, marker));
        Bounty claimed = placeFromHand(manager, sponsor, target).value();
        ServerPlayer sponsor2 = TestPlayers.create(helper, "sponsor2", new Vec3(1.5, 1, 4.5));
        hold(sponsor2, marked(Items.NETHERITE_SWORD, 1, marker));
        Bounty stillActive = placeFromHand(manager, sponsor2, target).value();
        // Make the hunter offline so the claimed reward stays a pending delivery.
        UUID hunterId = hunter.getUUID();
        TestPlayers.logout(hunter);
        manager.claimAll(target.getUUID(), hunterId);
        helper.assertTrue(manager.deliveriesFor(hunterId).size() == 2, "setup: expected two pending deliveries");

        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = manager.data().save(new CompoundTag(), registries);
        BountySavedData reloaded = BountySavedData.load(saved, registries);

        helper.assertTrue(reloaded.activeBounties().isEmpty(), "everything was claimed");
        helper.assertTrue(reloaded.history().size() == 2, "history lost");
        helper.assertTrue(reloaded.deliveries().size() == 2, "deliveries lost");
        int total = reloaded.deliveries().stream().filter(d -> TestSupport.isMarked(d.stack(), marker)).mapToInt(d -> d.stack().getCount()).sum();
        helper.assertTrue(total == 4, "delivered items changed across the reload");
        for (PendingDelivery d : manager.data().deliveries()) {
            helper.assertTrue(reloaded.deliveries().stream().anyMatch(r -> r.id().equals(d.id()) && ItemStack.matches(r.stack(), d.stack())),
                    "delivery " + d.id() + " differs after reload");
        }
        // Saving the reloaded copy yields the same NBT: nothing duplicated or dropped.
        helper.assertTrue(saved.equals(reloaded.save(new CompoundTag(), registries)), "second save differs from the first");
        helper.assertTrue(claimed != null && stillActive != null, "setup");
        helper.succeed();
    }

    @GameTest(template = T, batch = "persistence_active")
    public static void activeBountySurvivesRestart(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        UUID marker = UUID.randomUUID();
        ItemStack reward = marked(Items.ENCHANTED_BOOK, 1, marker);
        reward.enchant(helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 5);
        ItemStack expected = reward.copy();
        hold(sponsor, reward);
        Bounty b = placeFromHand(manager, sponsor, target).value();

        var registries = helper.getLevel().registryAccess();
        BountySavedData reloaded = BountySavedData.load(manager.data().save(new CompoundTag(), registries), registries);

        Bounty r = reloaded.active(b.id()).orElseThrow();
        helper.assertTrue(ItemStack.matches(expected, r.rewards().get(0)), "enchanted reward changed across reload");
        helper.assertTrue(r.sponsor().equals(b.sponsor()) && r.target().equals(b.target()) && r.createdAt() == b.createdAt(), "fields changed");
        helper.succeed();
    }

    @GameTest(template = T, batch = "unknown_item")
    public static void unknownItemOnLoadDoesNotCrash(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        hold(sponsor, new ItemStack(Items.DIAMOND));
        placeFromHand(manager, sponsor, target);
        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = manager.data().save(new CompoundTag(), registries);
        CompoundTag entry = saved.getList("Active", Tag.TAG_COMPOUND).getCompound(0);
        entry.getList("Rewards", Tag.TAG_COMPOUND).getCompound(0).putString("id", "removedmod:cursed_relic");

        BountySavedData reloaded = BountySavedData.load(saved, registries);

        helper.assertTrue(reloaded.activeBounties().isEmpty() && reloaded.unreadableEntries() == 1, "unknown item not quarantined");
        CompoundTag resaved = reloaded.save(new CompoundTag(), registries);
        helper.assertTrue(entry.equals(resaved.getList("Active", Tag.TAG_COMPOUND).getCompound(0)), "raw data not preserved");
        helper.succeed();
    }

    @GameTest(template = T, batch = "dimension")
    public static void dimensionChangeDoesNotMatter(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 3, marker));
        Bounty b = placeFromHand(manager, sponsor, target).value();
        var nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        helper.assertTrue(nether != null, "no nether");
        target.teleportTo(nether, 0, 100, 0, 0, 0);
        hunter.teleportTo(nether, 2, 100, 0, 0, 0);
        // Simulate the client acknowledging the dimension change (vanilla grants invulnerability until then).
        target.hasChangedDimension();
        hunter.hasChangedDimension();
        hit(target, target.damageSources().playerAttack(hunter), 1000f);
        helper.assertTrue(manager.findAny(b.id()).orElseThrow().state() == BountyState.CLAIMED, "not claimed in the nether");
        TestSupport.claimAll(helper, manager, hunter);
        helper.assertTrue(countInInventory(hunter, marker) == 3, "hunter not paid in the nether");
        helper.succeed();
    }
}
