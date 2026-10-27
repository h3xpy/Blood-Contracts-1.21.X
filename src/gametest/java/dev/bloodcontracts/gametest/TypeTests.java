package dev.bloodcontracts.gametest;

import static dev.bloodcontracts.gametest.LifecycleTests.expectRefused;
import static dev.bloodcontracts.gametest.TestSupport.assertConserved;
import static dev.bloodcontracts.gametest.TestSupport.hit;
import static dev.bloodcontracts.gametest.TestSupport.hold;
import static dev.bloodcontracts.gametest.TestSupport.marked;

import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.config.BCConfig;
import dev.bloodcontracts.core.Bounty;
import dev.bloodcontracts.core.BountyError;
import dev.bloodcontracts.core.BountyManager;
import dev.bloodcontracts.core.BountyResult;
import dev.bloodcontracts.core.BountyState;
import dev.bloodcontracts.core.BountyType;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Public and private bounties, widen-only changes. */
@GameTestHolder(BloodContracts.MODID)
@PrefixGameTestTemplate(false)
public class TypeTests {
    private static final String T = BloodContractsGameTests.TEMPLATE;

    static BountyResult<Bounty> placePrivate(BountyManager manager, ServerPlayer sponsor, ServerPlayer target, List<UUID> hunters) {
        return manager.create(sponsor, target.getUUID(), BountyType.PRIVATE, hunters, sponsor.getInventory(), List.of(sponsor.getInventory().selected));
    }

    @GameTest(template = T, batch = "type_private_kill")
    public static void privateBountyOnlyListedHuntersCollect(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        TestSupport.clock(manager);
        BCConfig.TARGET_IMMUNITY_MINUTES.set(0);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer listed = TestPlayers.hunter(helper);
        ServerPlayer outsider = TestPlayers.create(helper, "outsider", new Vec3(3.5, 1, 4.5));
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 2, marker));
        Bounty b = placePrivate(manager, sponsor, target, List.of(listed.getUUID())).value();
        helper.assertTrue(b != null && b.type() == BountyType.PRIVATE, "private bounty not created");

        // An outsider's kill pays nothing: the bounty stays active, no heart moves.
        manager.onDeath(target, target.damageSources().playerAttack(outsider));
        helper.assertTrue(manager.findActive(b.id()).isPresent(), "outsider collected a private bounty");
        helper.assertTrue(manager.heartBalanceHp(target.getUUID()) == 0, "heart moved without a claim");

        hit(target, target.damageSources().playerAttack(listed), 1000f);
        Bounty closed = manager.findAny(b.id()).orElseThrow();
        helper.assertTrue(closed.state() == BountyState.CLAIMED && listed.getUUID().equals(closed.claimedBy()), "listed hunter not paid");
        assertConserved(helper, manager, marker, 2);
        helper.succeed();
    }

    @GameTest(template = T, batch = "type_private_invalid")
    public static void invalidPrivateListsAreRefused(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        BCConfig.MAX_HUNTER_LIST_SIZE.set(2);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer a = TestPlayers.hunter(helper);
        ServerPlayer b = TestPlayers.create(helper, "hunterB", new Vec3(3.5, 1, 4.5));
        ServerPlayer c = TestPlayers.create(helper, "hunterC", new Vec3(3.5, 1, 6.5));
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 4, marker));
        ItemStack snapshot = sponsor.getInventory().getItem(0).copy();

        expectRefused(helper, placePrivate(manager, sponsor, target, List.of()), BountyError.INVALID_HUNTERS);
        expectRefused(helper, placePrivate(manager, sponsor, target, List.of(a.getUUID(), b.getUUID(), c.getUUID())), BountyError.INVALID_HUNTERS);
        expectRefused(helper, placePrivate(manager, sponsor, target, List.of(sponsor.getUUID())), BountyError.INVALID_HUNTERS);
        expectRefused(helper, placePrivate(manager, sponsor, target, List.of(target.getUUID())), BountyError.INVALID_HUNTERS);
        expectRefused(helper, placePrivate(manager, sponsor, target, List.of(UUID.randomUUID())), BountyError.INVALID_HUNTERS);
        helper.assertTrue(ItemStack.matches(snapshot, sponsor.getInventory().getItem(0)), "a refused creation touched the inventory");
        helper.assertTrue(manager.activeBounties().isEmpty(), "a refused creation created a bounty");
        assertConserved(helper, manager, marker, 4);
        helper.succeed();
    }

    @GameTest(template = T, batch = "type_widen")
    public static void privateBountiesCanOnlyBeWidened(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        BCConfig.TARGET_IMMUNITY_MINUTES.set(0);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer a = TestPlayers.hunter(helper);
        ServerPlayer late = TestPlayers.create(helper, "late", new Vec3(3.5, 1, 4.5));
        ServerPlayer stranger = TestPlayers.create(helper, "stranger", new Vec3(3.5, 1, 6.5));
        hold(sponsor, new ItemStack(Items.DIAMOND));
        Bounty b = placePrivate(manager, sponsor, target, List.of(a.getUUID())).value();

        // Only the sponsor can widen.
        expectRefused(helper, manager.addHunters(stranger.getUUID(), b.id(), List.of(stranger.getUUID())), BountyError.NOT_OWNER);
        expectRefused(helper, manager.makePublic(stranger.getUUID(), b.id()), BountyError.NOT_OWNER);
        // Adding a hunter makes them eligible, existing hunters stay.
        BountyResult<Bounty> widened = manager.addHunters(sponsor.getUUID(), b.id(), List.of(late.getUUID()));
        helper.assertTrue(widened.isOk() && widened.value().hunters().equals(List.of(a.getUUID(), late.getUUID())), "hunter not added");
        helper.assertTrue(manager.canCollect(late.getUUID(), widened.value()), "added hunter not eligible");
        helper.assertTrue(!manager.canCollect(stranger.getUUID(), widened.value()), "stranger eligible on a private bounty");
        // PRIVATE -> PUBLIC is allowed; afterwards everyone is eligible and there is no way back.
        BountyResult<Bounty> pub = manager.makePublic(sponsor.getUUID(), b.id());
        helper.assertTrue(pub.isOk() && pub.value().type() == BountyType.PUBLIC, "make public refused");
        helper.assertTrue(manager.canCollect(stranger.getUUID(), pub.value()), "public bounty not open to everyone");
        expectRefused(helper, manager.makePublic(sponsor.getUUID(), b.id()), BountyError.ALREADY_PUBLIC);
        expectRefused(helper, manager.addHunters(sponsor.getUUID(), b.id(), List.of(stranger.getUUID())), BountyError.ALREADY_PUBLIC);
        // A collected bounty cannot be changed any more.
        manager.onDeath(target, target.damageSources().playerAttack(stranger));
        expectRefused(helper, manager.makePublic(sponsor.getUUID(), b.id()), BountyError.NOT_ACTIVE);
        expectRefused(helper, manager.addHunters(sponsor.getUUID(), b.id(), List.of(late.getUUID())), BountyError.NOT_ACTIVE);
        // The manager has no narrowing operation at all.
        helper.assertTrue(java.util.Arrays.stream(BountyManager.class.getMethods()).noneMatch(m -> m.getName().toLowerCase().contains("removehunter")
                || m.getName().toLowerCase().contains("makeprivate")), "a narrowing operation exists");
        helper.succeed();
    }

    @GameTest(template = T, batch = "type_widen_limit")
    public static void wideningRespectsTheListSize(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        BCConfig.MAX_HUNTER_LIST_SIZE.set(2);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer a = TestPlayers.hunter(helper);
        ServerPlayer b = TestPlayers.create(helper, "hunterB", new Vec3(3.5, 1, 4.5));
        ServerPlayer c = TestPlayers.create(helper, "hunterC", new Vec3(3.5, 1, 6.5));
        hold(sponsor, new ItemStack(Items.DIAMOND));
        Bounty bounty = placePrivate(manager, sponsor, target, List.of(a.getUUID(), b.getUUID())).value();
        expectRefused(helper, manager.addHunters(sponsor.getUUID(), bounty.id(), List.of(c.getUUID())), BountyError.INVALID_HUNTERS);
        expectRefused(helper, manager.addHunters(sponsor.getUUID(), bounty.id(), List.of(target.getUUID())), BountyError.INVALID_HUNTERS);
        helper.assertTrue(manager.findActive(bounty.id()).orElseThrow().hunters().size() == 2, "refused widening changed the list");
        helper.succeed();
    }
}
