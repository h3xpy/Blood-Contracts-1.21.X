package dev.bloodcontracts.gametest;

import static dev.bloodcontracts.gametest.LifecycleTests.expectRefused;
import static dev.bloodcontracts.gametest.TestSupport.hold;
import static dev.bloodcontracts.gametest.TestSupport.placeFromHand;
import static dev.bloodcontracts.gametest.TypeTests.placePrivate;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.config.BCConfig;
import dev.bloodcontracts.core.Bounty;
import dev.bloodcontracts.core.BountyError;
import dev.bloodcontracts.core.BountyManager;
import dev.bloodcontracts.core.BountyState;
import dev.bloodcontracts.core.BountyType;
import dev.bloodcontracts.network.BCPayloads;
import dev.bloodcontracts.network.BCPayloads.AddHuntersRequest;
import dev.bloodcontracts.network.BCPayloads.BountyListPayload;
import dev.bloodcontracts.network.BCPayloads.CancelBountyRequest;
import dev.bloodcontracts.network.BCPayloads.HelpConfigPayload;
import dev.bloodcontracts.network.BCPayloads.MakePublicRequest;
import dev.bloodcontracts.network.BCPayloads.ResumeBountyRequest;
import dev.bloodcontracts.network.BountyView;
import dev.bloodcontracts.network.ServerPayloadHandler;
import dev.bloodcontracts.network.TargetView;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** v2 payloads, views and commands: widening, confidentiality, grouped posters, help settings, recurring pairs. */
@GameTestHolder(BloodContracts.MODID)
@PrefixGameTestTemplate(false)
public class V2NetworkTests {
    private static final String T = BloodContractsGameTests.TEMPLATE;

    /** What the client would decode from the wire. */
    private static BountyListPayload overTheWire(GameTestHelper helper, BountyListPayload payload) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        BountyListPayload.STREAM_CODEC.encode(buf, payload);
        return BountyListPayload.STREAM_CODEC.decode(buf);
    }

    private static BountyView only(BountyListPayload payload) {
        return payload.active().get(0);
    }

    @GameTest(template = T, batch = "v2net_widen")
    public static void widenPayloadsAreRevalidated(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.clock(manager);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer listed = TestPlayers.hunter(helper);
        ServerPlayer other = TestPlayers.create(helper, "other", new Vec3(3.5, 1, 4.5));
        hold(sponsor, new ItemStack(Items.DIAMOND));
        Bounty b = placePrivate(manager, sponsor, target, List.of(listed.getUUID())).value();

        // Someone else cannot widen, cancel or resume.
        expectRefused(helper, ServerPayloadHandler.handleAddHunters(other, new AddHuntersRequest(b.id(), List.of(other.getUUID()))), BountyError.NOT_OWNER);
        expectRefused(helper, ServerPayloadHandler.handleMakePublic(other, new MakePublicRequest(b.id())), BountyError.NOT_OWNER);
        expectRefused(helper, ServerPayloadHandler.handleCancel(other, new CancelBountyRequest(b.id())), BountyError.NOT_OWNER);
        expectRefused(helper, ServerPayloadHandler.handleResume(sponsor, new ResumeBountyRequest(b.id())), BountyError.NOT_CANCELLING);
        expectRefused(helper, ServerPayloadHandler.handleAddHunters(sponsor, new AddHuntersRequest(UUID.randomUUID(), List.of(other.getUUID()))), BountyError.NOT_FOUND);
        expectRefused(helper, ServerPayloadHandler.handleAddHunters(sponsor, new AddHuntersRequest(b.id(), List.of(UUID.randomUUID()))), BountyError.INVALID_HUNTERS);
        helper.assertTrue(manager.findActive(b.id()).orElseThrow().equals(b), "a refused payload changed the bounty");

        // The sponsor widens.
        helper.assertTrue(ServerPayloadHandler.handleAddHunters(sponsor, new AddHuntersRequest(b.id(), List.of(other.getUUID()))).isOk(), "add hunter refused");
        helper.assertTrue(manager.canCollect(other.getUUID(), manager.findActive(b.id()).orElseThrow()), "added hunter not eligible");
        helper.assertTrue(ServerPayloadHandler.handleCancel(sponsor, new CancelBountyRequest(b.id())).value().isCancelPending(), "notice not started");
        helper.assertTrue(ServerPayloadHandler.handleResume(sponsor, new ResumeBountyRequest(b.id())).isOk(), "resume refused");
        helper.assertTrue(ServerPayloadHandler.handleMakePublic(sponsor, new MakePublicRequest(b.id())).value().type() == BountyType.PUBLIC, "make public refused");

        // No payload can narrow a bounty.
        helper.assertTrue(Arrays.stream(BCPayloads.class.getDeclaredClasses()).map(Class::getSimpleName)
                .noneMatch(n -> n.contains("Remove") || n.contains("Private") || n.contains("Restrict")), "a narrowing payload exists");
        helper.succeed();
    }

    @GameTest(template = T, batch = "v2net_confidential")
    public static void hunterListOnlyReachesTheSponsor(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer listed = TestPlayers.hunter(helper);
        ServerPlayer other = TestPlayers.create(helper, "other", new Vec3(3.5, 1, 4.5));
        hold(sponsor, new ItemStack(Items.DIAMOND));
        Bounty b = placePrivate(manager, sponsor, target, List.of(listed.getUUID())).value();

        BountyView forOther = only(overTheWire(helper, ServerPayloadHandler.buildList(other, false, null, false)));
        helper.assertTrue(forOther.hunters().isEmpty(), "hunter list sent to a non-sponsor");
        helper.assertTrue(forOther.type() == BountyType.PRIVATE && !forOther.eligible(), "type or eligibility wrong for an outsider");
        BountyView forListed = only(overTheWire(helper, ServerPayloadHandler.buildList(listed, false, null, false)));
        helper.assertTrue(forListed.hunters().isEmpty(), "hunter list sent to a hunter");
        helper.assertTrue(forListed.eligible(), "listed hunter not told they can collect");
        BountyView forTarget = only(overTheWire(helper, ServerPayloadHandler.buildList(target, false, null, false)));
        helper.assertTrue(forTarget.hunters().isEmpty() && !forTarget.eligible(), "target view leaks the list");
        BountyView forSponsor = only(overTheWire(helper, ServerPayloadHandler.buildList(sponsor, false, null, false)));
        helper.assertTrue(forSponsor.hunters().isPresent() && forSponsor.hunters().get().size() == 1
                && forSponsor.hunters().get().get(0).id().equals(listed.getUUID()), "sponsor does not see their list");
        // Operators see it too (the GameTest server grants no operator level, so the view is built as for an operator).
        helper.assertTrue(BountyView.of(b, manager, other.getUUID(), true).hunters().isPresent(), "operator does not see the list");
        // The history keeps the secret as well.
        manager.adminCancel(b.id());
        BountyListPayload later = overTheWire(helper, ServerPayloadHandler.buildList(listed, false, null, false));
        helper.assertTrue(later.history().get(0).hunters().isEmpty(), "history leaks the list");
        helper.succeed();
    }

    @GameTest(template = T, batch = "v2net_grouped")
    public static void bountiesOnOneTargetMakeOnePosterWithTheSkin(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.TestClock clock = TestSupport.clock(manager);
        ServerPlayer s1 = TestPlayers.sponsor(helper);
        ServerPlayer s2 = TestPlayers.create(helper, "sponsor2", new Vec3(1.5, 1, 4.5));
        ServerPlayer viewer = TestPlayers.hunter(helper);
        // The target has a real skin, then goes offline.
        GameProfile profile = new GameProfile(UUID.randomUUID(), "skinned");
        profile.getProperties().put("textures", new Property("textures", "dGV4dHVyZXM=", "c2lnbmF0dXJl"));
        ServerPlayer target = TestPlayers.login(helper, profile, new Vec3(5.5, 1, 1.5));
        TestPlayers.logout(target);
        hold(s1, new ItemStack(Items.DIAMOND));
        Bounty first = manager.create(s1, profile.getId(), s1.getInventory(), s1.getInventory().selected).value();
        clock.advanceMinutes(1);
        hold(s2, new ItemStack(Items.EMERALD, 3));
        Bounty second = manager.create(s2, profile.getId(), s2.getInventory(), s2.getInventory().selected).value();
        clock.advanceMinutes(1);
        ServerPlayer other = TestPlayers.target(helper);
        hold(s1, new ItemStack(Items.GOLD_INGOT));
        placeFromHand(manager, s1, other);

        BountyListPayload view = overTheWire(helper, ServerPayloadHandler.buildList(viewer, false, null, false));
        helper.assertTrue(view.wanted().size() == 2, "expected 2 posters, got " + view.wanted().size());
        TargetView poster = view.wanted().stream().filter(t -> t.targetId().equals(profile.getId())).findFirst().orElseThrow();
        helper.assertTrue(poster.bounties().size() == 2 && poster.bounties().get(0).id().equals(first.id())
                && poster.bounties().get(1).id().equals(second.id()), "bounties not grouped in creation order");
        Property textures = poster.profile().getProperties().get("textures").stream().findFirst().orElse(null);
        helper.assertTrue(textures != null && textures.value().equals("dGV4dHVyZXM=") && "c2lnbmF0dXJl".equals(textures.signature()),
                "offline target's skin not sent");
        helper.assertTrue("skinned".equals(poster.targetName()), "name missing from the profile");
        helper.assertTrue(view.serverNow() == manager.now(), "server clock not sent");
        // Sorted: same eligibility, most recent first.
        helper.assertTrue(view.wanted().get(0).targetId().equals(other.getUUID()), "posters not sorted by recency");
        helper.succeed();
    }

    @GameTest(template = T, batch = "v2net_sorting")
    public static void collectablePostersComeFirst(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.TestClock clock = TestSupport.clock(manager);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer viewer = TestPlayers.hunter(helper);
        ServerPlayer old = TestPlayers.target(helper);
        ServerPlayer recent = TestPlayers.create(helper, "recent", new Vec3(5.5, 1, 4.5));
        hold(sponsor, new ItemStack(Items.DIAMOND));
        placeFromHand(manager, sponsor, old);
        clock.advanceMinutes(5);
        hold(sponsor, new ItemStack(Items.DIAMOND));
        placePrivate(manager, sponsor, recent, List.of(old.getUUID()));
        List<TargetView> wanted = ServerPayloadHandler.wanted(viewer);
        helper.assertTrue(wanted.size() == 2 && wanted.get(0).targetId().equals(old.getUUID()) && wanted.get(0).eligible()
                && !wanted.get(1).eligible(), "eligible posters must come first");
        helper.succeed();
    }

    @GameTest(template = T, batch = "v2net_immunity_view")
    public static void viewsCarryImmunityAndNotice(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.clock(manager);
        ServerPlayer s1 = TestPlayers.sponsor(helper);
        ServerPlayer s2 = TestPlayers.create(helper, "sponsor2", new Vec3(1.5, 1, 4.5));
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        hold(s1, new ItemStack(Items.DIAMOND));
        placeFromHand(manager, s1, target);
        manager.claimAll(target.getUUID(), hunter.getUUID());
        hold(s2, new ItemStack(Items.DIAMOND));
        Bounty b = placeFromHand(manager, s2, target).value();
        manager.requestCancel(s2.getUUID(), b.id());
        TargetView poster = overTheWire(helper, ServerPayloadHandler.buildList(hunter, false, null, false)).wanted().get(0);
        helper.assertTrue(poster.immuneUntil().orElse(0L) == manager.now() + BCConfig.targetImmunityMillis(), "immunity not in the view");
        helper.assertTrue(poster.bounties().get(0).cancelAt().orElse(0L) == manager.now() + BCConfig.cancelNoticeMillis(), "notice not in the view");
        helper.succeed();
    }

    @GameTest(template = T, batch = "v2net_help_config")
    public static void helpSettingsMatchTheServerConfig(GameTestHelper helper) {
        TestSupport.reset(helper);
        BCConfig.CANCEL_NOTICE_MINUTES.set(45);
        BCConfig.HEART_REGEN_ONLINE_STEP_MINUTES.set(80);
        BCConfig.HEART_REGEN_OFFLINE_STEP_MINUTES.set(600);
        BCConfig.TARGET_IMMUNITY_MINUTES.set(150);
        BCConfig.MAX_HEARTS_LOST.set(4);
        BCConfig.HEART_REGEN_MODE.set(BCConfig.HeartRegenMode.PERMANENT);
        ServerPlayer player = TestPlayers.sponsor(helper);
        List<HelpConfigPayload> sent = TestPlayers.sentPayloads(player, HelpConfigPayload.class);
        helper.assertTrue(sent.size() == 1, "settings not sent at login (" + sent.size() + ")");
        HelpConfigPayload p = sent.get(0);
        helper.assertTrue(p.cancelNoticeMinutes() == 45 && p.regenOnlineStepMinutes() == 80 && p.regenOfflineStepMinutes() == 600
                && p.immunityMinutes() == 150 && p.maxHeartsLost() == 4 && !p.progressiveRegen()
                && p.maxHeartsLostPerDeath() == 1 && p.maxHeartsGained() == 5 && p.maxActiveBountiesPerSponsor() == 3
                && p.maxHunterListSize() == 20 && p.attributionWindowTicks() == 200 && p.creditTamedPets(), "settings differ: " + p);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        HelpConfigPayload.STREAM_CODEC.encode(buf, p);
        helper.assertTrue(HelpConfigPayload.STREAM_CODEC.decode(buf).equals(p), "settings codec round trip");
        TestSupport.reset(helper);
        helper.succeed();
    }

    @GameTest(template = T, batch = "v2net_repeat_pairs")
    public static void recurringPairsShownAboveTheThreshold(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.clock(manager);
        BCConfig.TARGET_IMMUNITY_MINUTES.set(0);
        BCConfig.HISTORY_SIZE.set(1);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        ServerPlayer viewer = TestPlayers.create(helper, "viewer", new Vec3(3.5, 1, 6.5));
        for (int i = 0; i < 3; i++) {
            ServerPlayer sponsor = TestPlayers.create(helper, "sp" + i, new Vec3(1.5, 1, 1.5 + 2 * i));
            hold(sponsor, new ItemStack(Items.DIAMOND));
            placeFromHand(manager, sponsor, target);
            manager.claimAll(target.getUUID(), hunter.getUUID());
            if (i == 1) {
                helper.assertTrue(manager.repeatPairs(target.getUUID()).isEmpty(), "shown at the threshold (2), must be above it");
            }
        }
        // The history only keeps 1 bounty; the counter keeps all 3.
        helper.assertTrue(manager.history().size() == 1, "setup: history not purged");
        helper.assertTrue(manager.repeatPairs(target.getUUID()).size() == 1 && manager.repeatPairs(target.getUUID()).get(0).count() == 3,
                "pair counter lost with the history purge");
        // Shown on the target's poster and in the creation screen's data.
        ServerPlayer s = TestPlayers.create(helper, "sp9", new Vec3(1.5, 1, 7.5));
        hold(s, new ItemStack(Items.DIAMOND));
        placeFromHand(manager, s, target);
        TargetView poster = overTheWire(helper, ServerPayloadHandler.buildList(viewer, false, null, false)).wanted().get(0);
        helper.assertTrue(poster.repeatHunters().size() == 1 && poster.repeatHunters().get(0).count() == 3
                && poster.repeatHunters().get(0).hunterName().equals(hunter.getGameProfile().getName()), "pair not on the poster");
        helper.assertTrue(ServerPayloadHandler.knownPlayers(viewer).repeatPairs().size() == 1, "pair not sent to the creation screen");
        // And it survives a reload.
        var registries = helper.getLevel().registryAccess();
        var reloaded = dev.bloodcontracts.core.BountySavedData.load(manager.data().save(new net.minecraft.nbt.CompoundTag(), registries), registries);
        helper.assertTrue(reloaded.pairCount(hunter.getUUID(), target.getUUID()) == 3, "pair counter lost on reload");
        helper.succeed();
    }

    @GameTest(template = T, batch = "v2net_target_down")
    public static void sponsorIsToldTheirTargetWentDown(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.clock(manager);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        hold(sponsor, new ItemStack(Items.DIAMOND));
        placeFromHand(manager, sponsor, target);
        TestPlayers.sentPayloads(sponsor, BCPayloads.TargetDownPayload.class);
        manager.onDeath(target, target.damageSources().playerAttack(hunter));
        List<BCPayloads.TargetDownPayload> sent = TestPlayers.sentPayloads(sponsor, BCPayloads.TargetDownPayload.class);
        helper.assertTrue(sent.size() == 1, "sponsor not told once, got " + sent.size());
        BCPayloads.TargetDownPayload p = sent.get(0);
        helper.assertTrue(p.target().equals(target.getUUID()) && p.hunterName().equals(hunter.getGameProfile().getName())
                && p.heartGained(), "wrong notice: " + p);
        helper.assertTrue(TestPlayers.sentPayloads(hunter, BCPayloads.TargetDownPayload.class).isEmpty(), "hunter got the sponsor's notice");
        helper.succeed();
    }

    // ---------------------------------------------------------------- commands

    private static int run(ServerPlayer player, String command) {
        var server = player.getServer();
        var dispatcher = server.getCommands().getDispatcher();
        try {
            return dispatcher.execute(command, player.createCommandSourceStack());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return -1;
        }
    }

    @GameTest(template = T, batch = "v2cmd_widen")
    public static void commandsWidenButNeverNarrow(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.clock(manager);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer a = TestPlayers.hunter(helper);
        ServerPlayer late = TestPlayers.create(helper, "late", new Vec3(3.5, 1, 4.5));
        String targetName = target.getGameProfile().getName();

        hold(sponsor, new ItemStack(Items.DIAMOND));
        helper.assertTrue(run(sponsor, "bounty place " + targetName + " private") <= 0, "private without hunters accepted");
        helper.assertTrue(run(sponsor, "bounty place " + targetName + " private " + a.getGameProfile().getName()) == 1, "private place refused");
        Bounty b = manager.activeBounties().get(0);
        helper.assertTrue(b.type() == BountyType.PRIVATE && b.hunters().equals(List.of(a.getUUID())), "private bounty wrong");
        String id = b.shortId();

        helper.assertTrue(run(late, "bounty addhunter " + id + " " + late.getGameProfile().getName()) == 0, "non-sponsor could widen");
        helper.assertTrue(run(sponsor, "bounty addhunter " + id + " " + late.getGameProfile().getName()) == 1, "addhunter refused");
        helper.assertTrue(manager.findActive(b.id()).orElseThrow().hunters().size() == 2, "hunter not added");
        helper.assertTrue(run(sponsor, "bounty cancel " + id) == 1 && manager.findActive(b.id()).orElseThrow().isCancelPending(), "notice not started");
        helper.assertTrue(run(sponsor, "bounty resume " + id) == 1 && !manager.findActive(b.id()).orElseThrow().isCancelPending(), "resume failed");
        helper.assertTrue(run(sponsor, "bounty makepublic " + id) == 1
                && manager.findActive(b.id()).orElseThrow().type() == BountyType.PUBLIC, "makepublic failed");
        helper.assertTrue(run(sponsor, "bounty makepublic " + id) == 0, "makepublic twice accepted");

        // There is no command to narrow a bounty.
        var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("bounty");
        helper.assertTrue(root.getChildren().stream().map(n -> n.getName().toLowerCase())
                .noneMatch(n -> n.contains("remove") || n.contains("private") || n.contains("restrict")), "a narrowing command exists");
        // Players cannot use the admin branch.
        helper.assertTrue(run(sponsor, "bounty admin cancel " + id) < 0, "admin cancel open to players");
        helper.assertTrue(manager.findActive(b.id()).isPresent(), "bounty cancelled by a player through admin");
        helper.succeed();
    }

    @GameTest(template = T, batch = "v2cmd_admin")
    public static void adminCancelIsImmediateAndHeartsAreReadable(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.clock(manager);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        var server = helper.getLevel().getServer();
        var console = server.createCommandSourceStack();
        hold(sponsor, new ItemStack(Items.DIAMOND));
        placeFromHand(manager, sponsor, target);
        manager.claimAll(target.getUUID(), hunter.getUUID());
        hold(sponsor, new ItemStack(Items.EMERALD));
        ServerPlayer target2 = TestPlayers.create(helper, "target2", new Vec3(5.5, 1, 4.5));
        Bounty b = placeFromHand(manager, sponsor, target2).value();
        try {
            int hp = server.getCommands().getDispatcher().execute("bounty admin hearts " + target.getGameProfile().getName(), console);
            helper.assertTrue(hp == -2, "admin hearts reports " + hp);
            helper.assertTrue(server.getCommands().getDispatcher().execute("bounty admin cancel " + b.shortId(), console) == 1, "admin cancel refused");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalStateException(e);
        }
        helper.assertTrue(manager.findAny(b.id()).orElseThrow().state() == BountyState.CANCELLED, "admin cancel not immediate");
        helper.assertTrue(manager.heartBalanceHp(target.getUUID()) == -2, "admin hearts changed something");
        helper.succeed();
    }

    @GameTest(template = T, batch = "v2cmd_clear_immunity")
    public static void adminCanClearImmunity(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.clock(manager);
        ServerPlayer s1 = TestPlayers.sponsor(helper);
        ServerPlayer s2 = TestPlayers.create(helper, "sponsor2", new Vec3(1.5, 1, 4.5));
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        var server = helper.getLevel().getServer();
        var console = server.createCommandSourceStack();
        var dispatcher = server.getCommands().getDispatcher();
        hold(s1, new ItemStack(Items.DIAMOND));
        placeFromHand(manager, s1, target);
        manager.onDeath(target, target.damageSources().playerAttack(hunter));
        hold(s2, new ItemStack(Items.EMERALD));
        Bounty second = placeFromHand(manager, s2, target).value();
        helper.assertTrue(manager.immuneUntil(target.getUUID()).isPresent(), "setup: target not immune");
        String name = target.getGameProfile().getName();
        try {
            // players cannot use it
            helper.assertTrue(run(hunter, "bounty admin clearimmunity " + name) < 0, "players can clear immunity");
            helper.assertTrue(dispatcher.execute("bounty admin clearimmunity " + name, console) == 1, "clear refused");
            helper.assertTrue(dispatcher.execute("bounty admin clearimmunity " + name, console) == 0, "clearing twice should report not immune");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalStateException(e);
        }
        helper.assertTrue(manager.immuneUntil(target.getUUID()).isEmpty(), "still immune");
        // The cleared immunity is saved too.
        var registries = helper.getLevel().registryAccess();
        var reloaded = dev.bloodcontracts.core.BountySavedData.load(manager.data().save(new net.minecraft.nbt.CompoundTag(), registries), registries);
        helper.assertTrue(reloaded.immuneUntil(target.getUUID()).isEmpty(), "cleared immunity came back after reload");
        // Bounties on the target can be collected again at once.
        manager.onDeath(target, target.damageSources().playerAttack(hunter));
        helper.assertTrue(manager.findAny(second.id()).orElseThrow().state() == BountyState.CLAIMED, "not collectable after the clear");
        helper.succeed();
    }
}
