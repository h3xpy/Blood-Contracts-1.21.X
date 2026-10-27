package dev.bloodcontracts.gametest;

import static dev.bloodcontracts.gametest.TestSupport.assertConserved;
import static dev.bloodcontracts.gametest.TestSupport.countInInventory;
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
import dev.bloodcontracts.core.BountyState;
import dev.bloodcontracts.menu.BountyCreateMenu;
import dev.bloodcontracts.network.BCPayloads;
import dev.bloodcontracts.network.BCPayloads.BountyListPayload;
import dev.bloodcontracts.network.BCPayloads.CreateBountyRequest;
import dev.bloodcontracts.network.ServerPayloadHandler;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Situations where the mod could break or be abused: double clicks, disconnecting or dying mid-creation, deaths that do
 * not happen (totem, another mod cancelling), settings changed under existing data, a clock that jumps back, renamed
 * players, rewards too heavy for the network, other mods touching max health.
 */
@GameTestHolder(BloodContracts.MODID)
@PrefixGameTestTemplate(false)
public class AbuseTests {
    private static final String T = BloodContractsGameTests.TEMPLATE;

    // ---------------------------------------------------------------- items: duplication and loss

    @GameTest(template = T, batch = "abuse_double_click")
    public static void doubleSignMakesOneBounty(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        UUID marker = UUID.randomUUID();
        ServerPayloadHandler.handleOpenCreate(sponsor);
        ((BountyCreateMenu) sponsor.containerMenu).rewardContainer().setItem(0, marked(Items.DIAMOND, 5, marker));
        // A double click sends the same request twice before the menu closes on the client.
        var first = ServerPayloadHandler.handleCreate(sponsor, new CreateBountyRequest(target.getUUID()));
        var second = ServerPayloadHandler.handleCreate(sponsor, new CreateBountyRequest(target.getUUID()));
        helper.assertTrue(first.isOk(), "first sign refused: " + first.error());
        helper.assertTrue(!second.isOk() && second.error() == BountyError.NO_MENU, "second sign not refused: " + second.error());
        helper.assertTrue(manager.activeBounties().size() == 1, "two bounties from one stack");
        assertConserved(helper, manager, marker, 5);
        helper.succeed();
    }

    @GameTest(template = T, batch = "abuse_disconnect_menu")
    public static void disconnectingWithItemsInTheCreationMenuKeepsThem(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        UUID marker = UUID.randomUUID();
        GameProfile profile = sponsor.getGameProfile();
        ServerPayloadHandler.handleOpenCreate(sponsor);
        BountyCreateMenu menu = (BountyCreateMenu) sponsor.containerMenu;
        menu.rewardContainer().setItem(0, marked(Items.DIAMOND, 7, marker));
        // The real disconnection path: the player data is saved, then the player is removed from the world.
        sponsor.connection.onDisconnect(new DisconnectionDetails(Component.literal("test")));
        helper.assertTrue(helper.getLevel().getServer().getPlayerList().getPlayer(profile.getId()) == null, "setup: still connected");
        // Nothing may wait on the ground for the player to come back (it would despawn): the stack must be in the save.
        assertConserved(helper, manager, marker, 0);
        ServerPlayer back = TestPlayers.login(helper, profile, new Vec3(1.5, 1, 1.5));
        helper.assertTrue(countInInventory(back, marker) == 7, "items left in the creation menu were lost on disconnect: "
                + countInInventory(back, marker) + " back");
        helper.succeed();
    }

    @GameTest(template = T, batch = "abuse_death_menu")
    public static void dyingWithItemsInTheCreationMenuLosesNothing(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        UUID marker = UUID.randomUUID();
        ServerPayloadHandler.handleOpenCreate(sponsor);
        ((BountyCreateMenu) sponsor.containerMenu).rewardContainer().setItem(0, marked(Items.EMERALD, 9, marker));
        sponsor.kill();
        // The respawn can come right away (immediate respawn): the old player object goes away.
        ServerPlayer respawned = helper.getLevel().getServer().getPlayerList().respawn(sponsor, false, Entity.RemovalReason.KILLED);
        // Like a crafting grid: dropped at the place of death, never destroyed.
        assertConserved(helper, manager, marker, 9);
        helper.assertTrue(respawned.containerMenu == respawned.inventoryMenu, "creation menu survived the death");
        TestPlayers.logout(respawned); // the respawned player is a new object the test helpers do not track
        helper.succeed();
    }

    // ---------------------------------------------------------------- deaths that do not happen

    @GameTest(template = T, batch = "abuse_totem")
    public static void totemSavesTheTargetAndTheBountyStays(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        hold(sponsor, new ItemStack(Items.DIAMOND));
        Bounty b = placeFromHand(manager, sponsor, target).value();
        target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        hit(target, target.damageSources().playerAttack(hunter), 1000f);
        helper.assertTrue(target.isAlive(), "setup: the totem did not save the target");
        helper.assertTrue(manager.findActive(b.id()).isPresent(), "paid although the target survived");
        helper.assertTrue(manager.immuneUntil(target.getUUID()).isEmpty() && manager.heartBalanceHp(target.getUUID()) == 0,
                "immunity or heart without a death");
        helper.succeed();
    }

    /** Victim whose death another mod cancels (a revive mod, a "second chance"...), for the listener below. */
    private static volatile @Nullable UUID cancelDeathOf;
    private static boolean cancelListener;

    @GameTest(template = T, batch = "abuse_cancelled_death")
    public static void deathCancelledByAnotherModPaysNothing(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        if (!cancelListener) {
            cancelListener = true;
            NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH, (LivingDeathEvent e) -> {
                if (e.getEntity().getUUID().equals(cancelDeathOf)) {
                    e.setCanceled(true);
                    e.getEntity().setHealth(1);
                }
            });
        }
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        hold(sponsor, new ItemStack(Items.DIAMOND));
        Bounty b = placeFromHand(manager, sponsor, target).value();
        cancelDeathOf = target.getUUID();
        try {
            hit(target, target.damageSources().playerAttack(hunter), 1000f);
        } finally {
            cancelDeathOf = null;
        }
        helper.assertTrue(target.isAlive(), "setup: death not cancelled");
        helper.assertTrue(manager.findActive(b.id()).isPresent(), "paid for a cancelled death");
        helper.succeed();
    }

    // ---------------------------------------------------------------- settings changed under existing data

    @GameTest(template = T, batch = "abuse_bounds_lowered")
    public static void loweringHeartBoundsUnderExistingDebtsBreaksNothing(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.clock(manager);
        BCConfig.TARGET_IMMUNITY_MINUTES.set(0);
        ServerPlayer s1 = TestPlayers.sponsor(helper);
        ServerPlayer s2 = TestPlayers.create(helper, "sponsor2", new Vec3(1.5, 1, 4.5));
        ServerPlayer s3 = TestPlayers.create(helper, "sponsor3", new Vec3(1.5, 1, 6.5));
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        for (ServerPlayer s : List.of(s1, s2)) {
            hold(s, new ItemStack(Items.DIAMOND));
            placeFromHand(manager, s, target);
            manager.onDeath(target, target.damageSources().playerAttack(hunter));
        }
        helper.assertTrue(manager.heartBalanceHp(target.getUUID()) == -4, "setup: expected -2 hearts");
        // The admin lowers the floor below the current debt: existing transfers stay, new ones stop, nothing throws.
        BCConfig.MAX_HEARTS_LOST.set(1);
        hold(s3, new ItemStack(Items.DIAMOND));
        Bounty third = placeFromHand(manager, s3, target).value();
        manager.onDeath(target, target.damageSources().playerAttack(hunter));
        helper.assertTrue(manager.findAny(third.id()).orElseThrow().state() == BountyState.CLAIMED, "item not paid under the new floor");
        helper.assertTrue(manager.heartBalanceHp(target.getUUID()) == -4 && manager.heartBalanceHp(s3.getUUID()) == 0, "heart moved past the floor");
        BCConfig.MAX_HEARTS_GAINED.set(0);
        BCConfig.MAX_HEARTS_LOST.set(5);
        ServerPlayer target2 = TestPlayers.create(helper, "target2", new Vec3(5.5, 1, 4.5));
        hold(s1, new ItemStack(Items.DIAMOND));
        placeFromHand(manager, s1, target2);
        manager.onDeath(target2, target2.damageSources().playerAttack(hunter));
        helper.assertTrue(manager.heartBalanceHp(s1.getUUID()) == 2 && manager.heartBalanceHp(target2.getUUID()) == 0, "heart moved past the ceiling");
        helper.succeed();
    }

    @GameTest(template = T, batch = "abuse_mode_switch")
    public static void switchingPermanentToProgressiveIsNotRetroactive(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.TestClock clock = TestSupport.clock(manager);
        BCConfig.HEART_REGEN_MODE.set(BCConfig.HeartRegenMode.PERMANENT);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        hold(sponsor, new ItemStack(Items.DIAMOND));
        placeFromHand(manager, sponsor, target);
        manager.onDeath(target, target.damageSources().playerAttack(hunter));
        clock.advanceMinutes(48 * 60);
        manager.periodic();
        // "Changing the mode applies to existing transfers from the change on": 48 h of PERMANENT give nothing back.
        BCConfig.HEART_REGEN_MODE.set(BCConfig.HeartRegenMode.PROGRESSIVE);
        manager.periodic();
        helper.assertTrue(manager.heartBalanceHp(target.getUUID()) == -2, "time spent in PERMANENT mode was paid back");
        clock.advanceMinutes(90);
        manager.periodic();
        helper.assertTrue(manager.heartBalanceHp(target.getUUID()) == -1, "progressive return not running after the switch");
        helper.succeed();
    }

    // ---------------------------------------------------------------- clock

    @GameTest(template = T, batch = "abuse_clock_back")
    public static void clockGoingBackwardsGivesNothingAndBreaksNothing(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.TestClock clock = TestSupport.clock(manager);
        ServerPlayer s1 = TestPlayers.sponsor(helper);
        ServerPlayer s2 = TestPlayers.create(helper, "sponsor2", new Vec3(1.5, 1, 4.5));
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        hold(s1, new ItemStack(Items.DIAMOND));
        placeFromHand(manager, s1, target);
        manager.onDeath(target, target.damageSources().playerAttack(hunter));
        hold(s2, new ItemStack(Items.EMERALD));
        Bounty pending = placeFromHand(manager, s2, target).value();
        manager.requestCancel(s2.getUUID(), pending.id());
        // The server clock is set back by 3 hours (NTP correction, wrong date fixed...).
        clock.advanceMinutes(-180);
        manager.periodic();
        manager.onServerStopping();
        manager.onServerStarted();
        helper.assertTrue(manager.heartBalanceHp(target.getUUID()) == -2, "a clock going back gave hearts");
        double progress = manager.data().regen(target.getUUID()).orElseThrow().progress();
        helper.assertTrue(progress >= 0 && progress < 1, "gauge corrupted by a negative interval: " + progress);
        helper.assertTrue(manager.findActive(pending.id()).isPresent(), "notice finalized by a clock going back");
        // Once the clock is past the original deadlines again, everything resumes.
        clock.advanceMinutes(180 + 30);
        manager.periodic();
        helper.assertTrue(manager.findAny(pending.id()).orElseThrow().state() == BountyState.CANCELLED, "notice never ends");
        helper.succeed();
    }

    // ---------------------------------------------------------------- hearts

    @GameTest(template = T, batch = "abuse_mutual")
    public static void mutualBountiesKeepEveryBalanceConsistent(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.TestClock clock = TestSupport.clock(manager);
        ServerPlayer a = TestPlayers.sponsor(helper);
        ServerPlayer b = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        // A wants B dead, B wants A dead, the same hunter does both.
        hold(a, new ItemStack(Items.DIAMOND));
        placeFromHand(manager, a, b);
        hold(b, new ItemStack(Items.DIAMOND));
        placeFromHand(manager, b, a);
        manager.onDeath(b, b.damageSources().playerAttack(hunter));
        manager.onDeath(a, a.damageSources().playerAttack(hunter));
        helper.assertTrue(manager.heartBalanceHp(a.getUUID()) == 0 && manager.heartBalanceHp(b.getUUID()) == 0, "net balances wrong");
        helper.assertTrue(manager.data().transfers().size() == 2, "both transfers must be kept (each has its own return)");
        for (int i = 0; i < 4; i++) {
            clock.advanceMinutes(90);
            manager.periodic();
        }
        helper.assertTrue(manager.data().transfers().isEmpty(), "transfers not repaid");
        helper.assertTrue(manager.heartBalanceHp(a.getUUID()) == 0 && manager.heartBalanceHp(b.getUUID()) == 0, "balances drifted");
        helper.assertTrue(a.getMaxHealth() == 20 && b.getMaxHealth() == 20, "modifiers drifted");
        helper.succeed();
    }

    @GameTest(template = T, batch = "abuse_foreign_modifier")
    public static void otherModsLoweringMaxHealthNeverKill(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.clock(manager);
        BCConfig.TARGET_IMMUNITY_MINUTES.set(0);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        // Another mod already takes 18 of the 20 HP.
        var foreign = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("othermod", "curse");
        target.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(new AttributeModifier(foreign, -18, AttributeModifier.Operation.ADD_VALUE));
        for (int i = 0; i < 3; i++) {
            ServerPlayer s = TestPlayers.create(helper, "spons" + i, new Vec3(1.5, 1, 1.5 + 2 * i));
            hold(s, new ItemStack(Items.DIAMOND));
            placeFromHand(manager, s, target);
            manager.onDeath(target, target.damageSources().playerAttack(hunter));
        }
        helper.assertTrue(target.isAlive() && target.getHealth() > 0 && target.getMaxHealth() >= 1, "max health went to 0 or killed the player");
        helper.assertTrue(target.getAttribute(Attributes.MAX_HEALTH).getModifier(foreign) != null, "the other mod's modifier was touched");
        target.getAttribute(Attributes.MAX_HEALTH).removeModifier(foreign);
        helper.assertTrue(target.getMaxHealth() == 20 - 6, "our own modifier wrong once the other mod is gone: " + target.getMaxHealth());
        helper.succeed();
    }

    // ---------------------------------------------------------------- names

    @GameTest(template = T, batch = "abuse_renamed")
    public static void aNameTakenByAnotherPlayerResolvesToItsCurrentOwner(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        String name = "Renamed" + (System.nanoTime() % 100000);
        // An old account used the name, then renamed; a new account now has it. The old one has not come back since.
        GameProfile old = new GameProfile(UUID.randomUUID(), name);
        TestPlayers.logout(TestPlayers.login(helper, old, new Vec3(5.5, 1, 1.5)));
        GameProfile current = new GameProfile(UUID.randomUUID(), name);
        TestPlayers.logout(TestPlayers.login(helper, current, new Vec3(5.5, 1, 1.5)));
        helper.assertTrue(manager.findKnownPlayer(name).orElse(null) != null
                && manager.findKnownPlayer(name).get().equals(current.getId()), "a bounty by name would hit the wrong player");
        // The command too.
        hold(sponsor, new ItemStack(Items.DIAMOND));
        try {
            helper.getLevel().getServer().getCommands().getDispatcher().execute("bounty place " + name, sponsor.createCommandSourceStack());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalStateException(e);
        }
        helper.assertTrue(manager.activeBounties().size() == 1 && manager.activeBounties().get(0).target().equals(current.getId()),
                "the command placed the bounty on the wrong player");
        helper.succeed();
    }

    // ---------------------------------------------------------------- network size

    /** A shulker box full of books written to the limit: about 2.7 MB of data in one item. */
    private static ItemStack heavyShulker(UUID marker) {
        List<Filterable<String>> pages = new ArrayList<>();
        for (int i = 0; i < WritableBookContent.MAX_PAGES; i++) {
            pages.add(Filterable.passThrough("x".repeat(WritableBookContent.PAGE_EDIT_LENGTH)));
        }
        List<ItemStack> books = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
            book.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(pages));
            books.add(book);
        }
        ItemStack shulker = marked(Items.SHULKER_BOX, 1, marker);
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(books));
        return shulker;
    }

    @GameTest(template = T, batch = "abuse_heavy_reward", timeoutTicks = 400)
    public static void heavyRewardsDoNotOverflowTheNetwork(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer viewer = TestPlayers.hunter(helper);
        UUID marker = UUID.randomUUID();
        ItemStack heavy = heavyShulker(marker);
        ItemStack original = heavy.copy();
        hold(sponsor, heavy);
        Bounty b = placeFromHand(manager, sponsor, target).value();
        helper.assertTrue(b != null, "setup: bounty not placed");
        // The escrow keeps everything.
        helper.assertTrue(ItemStack.matches(original, b.rewards().get(0)), "escrow lost the shulker's contents");

        // Every payload a client receives about it must stay far below the 1 MiB limit of a custom payload,
        // or opening the board would disconnect every player.
        int limit = 1 << 20;
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        BountyListPayload.STREAM_CODEC.encode(buf, ServerPayloadHandler.buildList(viewer, false, null, false));
        int listSize = buf.readableBytes();
        helper.assertTrue(listSize < limit / 8, "board payload is " + listSize + " bytes");
        var alert = new BCPayloads.BountyAlertPayload(java.util.Optional.empty(), b.displayStacks(), 1, false);
        buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        BCPayloads.BountyAlertPayload.STREAM_CODEC.encode(buf, sentAlert(target, alert));
        helper.assertTrue(buf.readableBytes() < limit / 8, "alert payload is " + buf.readableBytes() + " bytes");
        // Collected: the delivery view sent to the hunter is light too, and the hunter gets the real item.
        manager.onDeath(target, target.damageSources().playerAttack(viewer));
        buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        BountyListPayload.STREAM_CODEC.encode(buf, ServerPayloadHandler.buildList(viewer, false, null, false));
        helper.assertTrue(buf.readableBytes() < limit / 8, "board payload with the delivery is " + buf.readableBytes() + " bytes");
        TestSupport.claimAll(helper, manager, viewer);
        helper.assertTrue(java.util.stream.IntStream.range(0, viewer.getInventory().getContainerSize())
                .anyMatch(i -> ItemStack.matches(original, viewer.getInventory().getItem(i))), "the hunter did not get the full shulker");
        helper.succeed();
    }

    /** The alert the target actually received when the bounty was placed (falls back to the given one). */
    private static BCPayloads.BountyAlertPayload sentAlert(ServerPlayer target, BCPayloads.BountyAlertPayload fallback) {
        List<BCPayloads.BountyAlertPayload> sent = TestPlayers.sentPayloads(target, BCPayloads.BountyAlertPayload.class);
        return sent.isEmpty() ? fallback : sent.get(sent.size() - 1);
    }
}
