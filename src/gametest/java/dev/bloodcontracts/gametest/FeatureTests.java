package dev.bloodcontracts.gametest;

import static dev.bloodcontracts.gametest.LifecycleTests.expectRefused;
import static dev.bloodcontracts.gametest.TestSupport.assertConserved;
import static dev.bloodcontracts.gametest.TestSupport.countInInventory;
import static dev.bloodcontracts.gametest.TestSupport.countOwed;
import static dev.bloodcontracts.gametest.TestSupport.hit;
import static dev.bloodcontracts.gametest.TestSupport.hold;
import static dev.bloodcontracts.gametest.TestSupport.marked;
import static dev.bloodcontracts.gametest.TestSupport.placeFromHand;

import com.mojang.authlib.GameProfile;
import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.config.BCConfig;
import dev.bloodcontracts.core.Bounty;
import dev.bloodcontracts.core.BountyError;
import dev.bloodcontracts.core.BountyManager;
import dev.bloodcontracts.core.BountyResult;
import dev.bloodcontracts.core.BountyState;
import dev.bloodcontracts.core.PendingDelivery;
import dev.bloodcontracts.menu.BountyCreateMenu;
import dev.bloodcontracts.network.BCPayloads.BountyAlertPayload;
import dev.bloodcontracts.network.BCPayloads.ClaimRewardRequest;
import dev.bloodcontracts.network.BCPayloads.CreateBountyRequest;
import dev.bloodcontracts.network.BCPayloads.KnownPlayersPayload;
import dev.bloodcontracts.network.BCPayloads.RewardsAvailablePayload;
import dev.bloodcontracts.network.ServerPayloadHandler;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Multi-item rewards, rewards claimed from the board, offline targets and the target alert. */
@GameTestHolder(BloodContracts.MODID)
@PrefixGameTestTemplate(false)
public class FeatureTests {
    private static final String T = BloodContractsGameTests.TEMPLATE;

    private static BountyCreateMenu openMenu(ServerPlayer player) {
        ServerPayloadHandler.handleOpenCreate(player);
        return (BountyCreateMenu) player.containerMenu;
    }

    // ---------------------------------------------------------------- several items

    @GameTest(template = T, batch = "multi_items_create")
    public static void expandedMenuEscrowsEveryStack(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        UUID marker = UUID.randomUUID();
        BountyCreateMenu menu = openMenu(sponsor);
        helper.assertTrue(!menu.isExpanded(), "menu should start with a single slot");
        helper.assertTrue(menu.clickMenuButton(sponsor, BountyCreateMenu.BUTTON_TOGGLE_SLOTS) && menu.isExpanded(), "could not expand");
        menu.rewardContainer().setItem(0, marked(Items.DIAMOND, 3, marker));
        menu.rewardContainer().setItem(4, marked(Items.EMERALD, 10, marker));
        menu.rewardContainer().setItem(8, marked(Items.NETHERITE_INGOT, 1, marker));

        BountyResult<Bounty> result = ServerPayloadHandler.handleCreate(sponsor, new CreateBountyRequest(target.getUUID()));

        helper.assertTrue(result.isOk(), "multi-item creation failed: " + result.error());
        Bounty b = result.value();
        helper.assertTrue(b.rewards().size() == 3, "expected 3 stacks, got " + b.rewards().size());
        for (int i = 0; i < BountyCreateMenu.REWARD_SLOTS; i++) {
            helper.assertTrue(menu.rewardContainer().getItem(i).isEmpty(), "slot " + i + " not emptied");
        }
        assertConserved(helper, manager, marker, 14);
        helper.succeed();
    }

    @GameTest(template = T, batch = "multi_items_refused")
    public static void oneBlacklistedStackRefusesTheWholeBounty(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        BCConfig.REWARD_BLACKLIST.set(List.of("minecraft:bedrock"));
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        UUID marker = UUID.randomUUID();
        BountyCreateMenu menu = openMenu(sponsor);
        menu.clickMenuButton(sponsor, BountyCreateMenu.BUTTON_TOGGLE_SLOTS);
        menu.rewardContainer().setItem(0, marked(Items.DIAMOND, 3, marker));
        menu.rewardContainer().setItem(1, marked(Items.BEDROCK, 1, marker));

        expectRefused(helper, ServerPayloadHandler.handleCreate(sponsor, new CreateBountyRequest(target.getUUID())), BountyError.BLACKLISTED);

        helper.assertTrue(menu.rewardContainer().getItem(0).getCount() == 3 && menu.rewardContainer().getItem(1).getCount() == 1,
                "a refused multi-item creation moved something");
        helper.assertTrue(manager.activeBounties().isEmpty(), "bounty created anyway");
        assertConserved(helper, manager, marker, 4);
        sponsor.closeContainer();
        helper.assertTrue(countInInventory(sponsor, marker) == 4, "items not returned when the menu closed");
        helper.succeed();
    }

    @GameTest(template = T, batch = "multi_items_collapse")
    public static void collapsingIsRefusedWhileExtraSlotsHoldItems(GameTestHelper helper) {
        TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        BountyCreateMenu menu = openMenu(sponsor);
        menu.clickMenuButton(sponsor, BountyCreateMenu.BUTTON_TOGGLE_SLOTS);
        menu.rewardContainer().setItem(5, new ItemStack(Items.GOLD_INGOT, 2));
        helper.assertTrue(!menu.clickMenuButton(sponsor, BountyCreateMenu.BUTTON_TOGGLE_SLOTS) && menu.isExpanded(),
                "collapsing hid an item");
        menu.rewardContainer().setItem(5, ItemStack.EMPTY);
        helper.assertTrue(menu.clickMenuButton(sponsor, BountyCreateMenu.BUTTON_TOGGLE_SLOTS) && !menu.isExpanded(), "could not collapse");
        helper.assertTrue(!menu.clickMenuButton(sponsor, 99), "unknown button accepted");
        helper.succeed();
    }

    @GameTest(template = T, batch = "multi_items_claim")
    public static void hunterClaimsEveryStackOfEveryBounty(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        UUID marker = UUID.randomUUID();
        BountyCreateMenu menu = openMenu(sponsor);
        menu.clickMenuButton(sponsor, BountyCreateMenu.BUTTON_TOGGLE_SLOTS);
        menu.rewardContainer().setItem(0, marked(Items.DIAMOND, 3, marker));
        menu.rewardContainer().setItem(1, marked(Items.EMERALD, 5, marker));
        helper.assertTrue(ServerPayloadHandler.handleCreate(sponsor, new CreateBountyRequest(target.getUUID())).isOk(), "setup");

        hit(target, target.damageSources().playerAttack(hunter), 1000f);

        List<PendingDelivery> owed = manager.deliveriesFor(hunter.getUUID());
        helper.assertTrue(owed.size() == 2, "expected one delivery per stack, got " + owed.size());
        helper.assertTrue(countInInventory(hunter, marker) == 0, "reward pushed into the inventory without a claim");
        // Claim one, then the rest.
        helper.assertTrue(ServerPayloadHandler.handleClaim(hunter, new ClaimRewardRequest(Optional.of(owed.get(0).id()))).value() == 1, "single claim");
        helper.assertTrue(countOwed(manager, hunter, marker) == countIf(owed.get(1).stack()), "wrong stack left");
        helper.assertTrue(ServerPayloadHandler.handleClaim(hunter, new ClaimRewardRequest(Optional.empty())).value() == 1, "claim all");
        helper.assertTrue(countInInventory(hunter, marker) == 8, "hunter should hold 8 marked items");
        assertConserved(helper, manager, marker, 8);
        helper.succeed();
    }

    private static int countIf(ItemStack stack) {
        return stack.getCount();
    }

    // ---------------------------------------------------------------- claims

    @GameTest(template = T, batch = "claim_invalid")
    public static void claimsAreRevalidated(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 3, marker));
        placeFromHand(manager, sponsor, target);
        hit(target, target.damageSources().playerAttack(hunter), 1000f);
        UUID deliveryId = manager.deliveriesFor(hunter.getUUID()).get(0).id();

        // Someone else's delivery, an unknown id, nothing owed.
        expectRefused(helper, ServerPayloadHandler.handleClaim(sponsor, new ClaimRewardRequest(Optional.of(deliveryId))), BountyError.NOT_FOUND);
        expectRefused(helper, ServerPayloadHandler.handleClaim(hunter, new ClaimRewardRequest(Optional.of(UUID.randomUUID()))), BountyError.NOT_FOUND);
        expectRefused(helper, ServerPayloadHandler.handleClaim(sponsor, new ClaimRewardRequest(Optional.empty())), BountyError.NOTHING_TO_CLAIM);
        // A dead player cannot claim (the item would drop on the death screen).
        expectRefused(helper, manager.claimRewards(target, Optional.empty()), BountyError.NOT_ALIVE);
        helper.assertTrue(countOwed(manager, hunter, marker) == 3, "a refused claim changed the delivery");
        assertConserved(helper, manager, marker, 3);

        helper.assertTrue(ServerPayloadHandler.handleClaim(hunter, new ClaimRewardRequest(Optional.of(deliveryId))).isOk(), "valid claim refused");
        expectRefused(helper, ServerPayloadHandler.handleClaim(hunter, new ClaimRewardRequest(Optional.of(deliveryId))), BountyError.NOT_FOUND);
        helper.assertTrue(countInInventory(hunter, marker) == 3, "claimed twice or not at all");
        assertConserved(helper, manager, marker, 3);
        helper.succeed();
    }

    @GameTest(template = T, batch = "claim_notify")
    public static void hunterIsToldRewardsAreWaiting(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        hold(sponsor, new ItemStack(Items.DIAMOND, 2));
        placeFromHand(manager, sponsor, target);
        TestPlayers.sentPayloads(hunter, RewardsAvailablePayload.class);

        hit(target, target.damageSources().playerAttack(hunter), 1000f);

        List<RewardsAvailablePayload> sent = TestPlayers.sentPayloads(hunter, RewardsAvailablePayload.class);
        helper.assertTrue(sent.size() == 1 && sent.get(0).count() == 1, "hunter not notified: " + sent);
        helper.assertTrue(TestPlayers.sentPayloads(sponsor, RewardsAvailablePayload.class).isEmpty(), "sponsor wrongly notified");

        // Reconnecting with rewards still waiting notifies again.
        GameProfile profile = hunter.getGameProfile();
        TestPlayers.logout(hunter);
        ServerPlayer back = TestPlayers.login(helper, profile, new Vec3(3.5, 1, 1.5));
        helper.assertTrue(!TestPlayers.sentPayloads(back, RewardsAvailablePayload.class).isEmpty(), "not notified on login");
        helper.succeed();
    }

    // ---------------------------------------------------------------- offline targets

    @GameTest(template = T, batch = "offline_target_menu")
    public static void bountyOnOfflinePlayerFromTheMenu(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        UUID targetId = target.getUUID();
        String targetName = target.getGameProfile().getName();
        TestPlayers.logout(target);

        BountyCreateMenu menu = openMenu(sponsor);
        List<KnownPlayersPayload> known = TestPlayers.sentPayloads(sponsor, KnownPlayersPayload.class);
        helper.assertTrue(!known.isEmpty(), "known players not sent when the menu opened");
        var entry = known.get(known.size() - 1).players().stream().filter(p -> p.id().equals(targetId)).findFirst();
        helper.assertTrue(entry.isPresent() && !entry.get().online() && entry.get().name().equals(targetName),
                "offline target missing from the picker: " + known.get(known.size() - 1).players());
        helper.assertTrue(known.get(known.size() - 1).players().stream().noneMatch(p -> p.id().equals(sponsor.getUUID())),
                "the sponsor should not be offered as a target");

        UUID marker = UUID.randomUUID();
        menu.rewardContainer().setItem(0, marked(Items.DIAMOND, 2, marker));
        BountyResult<Bounty> result = ServerPayloadHandler.handleCreate(sponsor, new CreateBountyRequest(targetId));
        helper.assertTrue(result.isOk(), "bounty on an offline player refused: " + result.error());
        helper.assertTrue(result.value().state() == BountyState.ACTIVE, "not active");
        assertConserved(helper, manager, marker, 2);
        helper.succeed();
    }

    @GameTest(template = T, batch = "offline_target_command")
    public static void bountyOnOfflinePlayerByCommand(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        String targetName = target.getGameProfile().getName();
        UUID targetId = target.getUUID();
        TestPlayers.logout(target);
        sponsor.server.getPlayerList().op(sponsor.getGameProfile());
        hold(sponsor, new ItemStack(Items.EMERALD, 4));

        sponsor.server.getCommands().performPrefixedCommand(sponsor.createCommandSourceStack(), "bounty place " + targetName.toUpperCase());

        helper.assertTrue(manager.activeOn(targetId).size() == 1, "command did not place a bounty on the offline player");
        helper.assertTrue(sponsor.getInventory().getItem(0).isEmpty(), "item not escrowed");
        sponsor.server.getCommands().performPrefixedCommand(sponsor.createCommandSourceStack(), "bounty place NobodyEverJoined");
        helper.assertTrue(manager.activeBounties().size() == 1, "unknown name accepted");
        helper.succeed();
    }

    // ---------------------------------------------------------------- alert

    @GameTest(template = T, batch = "alert_online")
    public static void onlineTargetGetsTheAlert(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer bystander = TestPlayers.hunter(helper);
        TestPlayers.sentPayloads(target, BountyAlertPayload.class);
        hold(sponsor, new ItemStack(Items.DIAMOND, 7));

        placeFromHand(manager, sponsor, target);

        List<BountyAlertPayload> alerts = TestPlayers.sentPayloads(target, BountyAlertPayload.class);
        helper.assertTrue(alerts.size() == 1, "target alerted " + alerts.size() + " times");
        BountyAlertPayload alert = alerts.get(0);
        helper.assertTrue(!alert.onLogin() && alert.bounties() == 1, "wrong alert kind");
        helper.assertTrue(alert.sponsorName().filter(sponsor.getGameProfile().getName()::equals).isPresent(), "alert should name the sponsor");
        helper.assertTrue(alert.rewards().size() == 1 && alert.rewards().get(0).getCount() == 7, "alert rewards wrong");
        helper.assertTrue(TestPlayers.sentPayloads(bystander, BountyAlertPayload.class).isEmpty(), "bystander alerted");
        helper.assertTrue(TestPlayers.sentPayloads(sponsor, BountyAlertPayload.class).isEmpty(), "sponsor alerted");

        // With alertRevealsSponsor off, the sponsor stays anonymous.
        BCConfig.ALERT_REVEALS_SPONSOR.set(false);
        ServerPlayer sponsor2 = TestPlayers.create(helper, "sponsor2", new Vec3(1.5, 1, 4.5));
        hold(sponsor2, new ItemStack(Items.DIAMOND, 1));
        placeFromHand(manager, sponsor2, target);
        BountyAlertPayload second = TestPlayers.sentPayloads(target, BountyAlertPayload.class).get(0);
        helper.assertTrue(second.sponsorName().isEmpty() && second.bounties() == 2, "anonymous alert wrong");
        helper.succeed();
    }

    @GameTest(template = T, batch = "alert_login")
    public static void offlineTargetIsAlertedOnLogin(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        GameProfile profile = target.getGameProfile();
        TestPlayers.logout(target);
        hold(sponsor, new ItemStack(Items.DIAMOND, 1));
        helper.assertTrue(manager.create(sponsor, profile.getId(), sponsor.getInventory(), 0).isOk(), "setup");

        ServerPlayer back = TestPlayers.login(helper, profile, new Vec3(5.5, 1, 1.5));

        List<BountyAlertPayload> alerts = TestPlayers.sentPayloads(back, BountyAlertPayload.class);
        helper.assertTrue(alerts.size() == 1 && alerts.get(0).onLogin() && alerts.get(0).bounties() == 1, "no login alert: " + alerts);
        // A player with no bounty on their head is not alerted.
        TestPlayers.logout(sponsor);
        ServerPlayer sponsorBack = TestPlayers.login(helper, sponsor.getGameProfile(), new Vec3(1.5, 1, 1.5));
        helper.assertTrue(TestPlayers.sentPayloads(sponsorBack, BountyAlertPayload.class).isEmpty(), "sponsor alerted on login");
        helper.succeed();
    }

    // ---------------------------------------------------------------- players who joined before the mod

    @GameTest(template = T, batch = "import_known_players")
    public static void playersWithASaveBecomeTargets(GameTestHelper helper) throws java.io.IOException {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        UUID veteran = UUID.randomUUID(), nameless = UUID.randomUUID();
        // A playerdata folder as the server leaves it: player files plus unrelated ones.
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("bc-playerdata");
        for (String f : List.of(veteran + ".dat", nameless + ".dat", veteran + ".dat_old", "notauuid.dat")) {
            java.nio.file.Files.writeString(dir.resolve(f), "");
        }
        List<UUID> found = BountyManager.listPlayerData(dir);
        helper.assertTrue(found.size() == 2 && found.containsAll(List.of(veteran, nameless)), "playerdata scan wrong: " + found);

        int added = manager.importKnownPlayers(found, id -> id.equals(veteran) ? Optional.of("OldTimer") : Optional.empty());

        helper.assertTrue(added == 1, "expected 1 import, got " + added);
        helper.assertTrue(manager.isKnownPlayer(veteran) && manager.nameOf(veteran).equals("OldTimer"), "veteran not registered");
        helper.assertTrue(manager.data().knownName(nameless).isEmpty(), "a player without a known name was registered");
        // Known players are never renamed by an import, and importing twice adds nothing.
        helper.assertTrue(manager.importKnownPlayers(List.of(veteran, sponsor.getUUID()), id -> Optional.of("Renamed")) == 0, "re-import added players");
        helper.assertTrue(manager.nameOf(veteran).equals("OldTimer"), "import renamed a known player");
        // The veteran shows up in the creation screen and can be targeted without having logged in.
        openMenu(sponsor);
        List<KnownPlayersPayload> known = TestPlayers.sentPayloads(sponsor, KnownPlayersPayload.class);
        helper.assertTrue(known.get(known.size() - 1).players().stream().anyMatch(p -> p.id().equals(veteran) && !p.online()),
                "imported player missing from the picker");
        UUID marker = UUID.randomUUID();
        ((BountyCreateMenu) sponsor.containerMenu).rewardContainer().setItem(0, marked(Items.DIAMOND, 1, marker));
        helper.assertTrue(ServerPayloadHandler.handleCreate(sponsor, new CreateBountyRequest(veteran)).isOk(), "bounty on imported player refused");
        assertConserved(helper, manager, marker, 1);
        helper.succeed();
    }

    // ---------------------------------------------------------------- skins of offline players

    @GameTest(template = T, batch = "offline_skins")
    public static void offlinePlayersKeepTheirSkin(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer viewer = TestPlayers.sponsor(helper);
        GameProfile profile = new GameProfile(UUID.randomUUID(), "SkinnedGuy");
        profile.getProperties().put("textures", new com.mojang.authlib.properties.Property("textures", "c2tpbi12YWx1ZQ==", "c2lnbmF0dXJl"));
        ServerPlayer skinned = TestPlayers.login(helper, profile, new Vec3(5.5, 1, 1.5));
        helper.assertTrue(manager.data().skin(skinned.getUUID()).filter(p -> p.value().equals("c2tpbi12YWx1ZQ==")
                && "c2lnbmF0dXJl".equals(p.signature())).isPresent(), "skin not recorded at login");
        TestPlayers.logout(skinned);

        // Offline now: the viewer still receives the recorded skin with the known players (creation menu and board).
        ServerPayloadHandler.handleListRequest(viewer);
        List<KnownPlayersPayload> sent = TestPlayers.sentPayloads(viewer, KnownPlayersPayload.class);
        helper.assertTrue(!sent.isEmpty(), "known players not sent with the board");
        var entry = sent.get(sent.size() - 1).players().stream().filter(p -> p.id().equals(profile.getId())).findFirst();
        helper.assertTrue(entry.isPresent() && !entry.get().online(), "offline player missing");
        helper.assertTrue(entry.get().skin().filter("c2tpbi12YWx1ZQ=="::equals).isPresent()
                && entry.get().skinSignature().filter("c2lnbmF0dXJl"::equals).isPresent(), "skin not sent for the offline player");

        // Survives a restart.
        var registries = helper.getLevel().registryAccess();
        var reloaded = dev.bloodcontracts.core.BountySavedData.load(manager.data().save(new net.minecraft.nbt.CompoundTag(), registries), registries);
        helper.assertTrue(reloaded.skin(profile.getId()).filter(p -> p.value().equals("c2tpbi12YWx1ZQ==")).isPresent(), "skin lost on reload");
        helper.succeed();
    }
}
