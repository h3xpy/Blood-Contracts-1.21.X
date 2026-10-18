package dev.bloodcontracts.gametest;

import static dev.bloodcontracts.gametest.TestSupport.assertConserved;
import static dev.bloodcontracts.gametest.TestSupport.hit;
import static dev.bloodcontracts.gametest.TestSupport.hold;
import static dev.bloodcontracts.gametest.TestSupport.marked;
import static dev.bloodcontracts.gametest.TestSupport.placeFromHand;

import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.config.BCConfig;
import dev.bloodcontracts.core.Bounty;
import dev.bloodcontracts.core.BountyManager;
import dev.bloodcontracts.core.BountyState;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Kill attribution with real damage and real deaths. */
@GameTestHolder(BloodContracts.MODID)
@PrefixGameTestTemplate(false)
public class AttributionTests {
    private static final String T = BloodContractsGameTests.TEMPLATE;

    /** sponsor, target, hunter and one active bounty on target funded by sponsor. */
    record Scene(BountyManager manager, ServerPlayer sponsor, ServerPlayer target, ServerPlayer hunter, Bounty bounty, UUID marker) {
        DamageSources sources() {
            return target.damageSources();
        }

        void assertClaimedBy(GameTestHelper helper, ServerPlayer expected) {
            Bounty b = manager.findAny(bounty.id()).orElseThrow();
            helper.assertTrue(b.state() == BountyState.CLAIMED, "bounty not claimed (state " + b.state() + ")");
            helper.assertTrue(expected.getUUID().equals(b.claimedBy()), "claimed by the wrong player: " + b.claimedBy());
            helper.assertTrue(TestSupport.countOwed(manager, expected, marker) == 3, "reward not waiting for the hunter");
            assertConserved(helper, manager, marker, 3);
            TestSupport.claimAll(helper, manager, expected);
            helper.assertTrue(TestSupport.countInInventory(expected, marker) == 3, "hunter did not receive the reward");
            assertConserved(helper, manager, marker, 3);
        }

        void assertStillActive(GameTestHelper helper) {
            helper.assertTrue(manager.findActive(bounty.id()).isPresent(), "bounty should still be active");
            helper.assertTrue(manager.data().deliveries().isEmpty(), "no delivery expected");
            assertConserved(helper, manager, marker, 3);
        }
    }

    static Scene scene(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 3, marker));
        Bounty b = placeFromHand(manager, sponsor, target).value();
        helper.assertTrue(b != null, "setup: bounty creation failed");
        return new Scene(manager, sponsor, target, hunter, b, marker);
    }

    @GameTest(template = T, batch = "kill_sword")
    public static void swordKillCreditsHunter(GameTestHelper helper) {
        Scene s = scene(helper);
        hit(s.target(), s.sources().playerAttack(s.hunter()), 1000f);
        helper.assertTrue(s.target().isDeadOrDying(), "target did not die");
        s.assertClaimedBy(helper, s.hunter());
        helper.succeed();
    }

    @GameTest(template = T, batch = "kill_arrow", timeoutTicks = 100)
    public static void arrowKillCreditsShooterNotArrow(GameTestHelper helper) {
        Scene s = scene(helper);
        s.target().setHealth(1f);
        Vec3 targetPos = s.target().position();
        Arrow arrow = new Arrow(helper.getLevel(), targetPos.x - 3, targetPos.y + 1, targetPos.z, new ItemStack(Items.ARROW), null);
        arrow.setOwner(s.hunter());
        arrow.setBaseDamage(50);
        arrow.shoot(1, 0, 0, 2f, 0f);
        helper.getLevel().addFreshEntity(arrow);
        helper.succeedWhen(() -> {
            helper.assertTrue(s.target().isDeadOrDying(), "arrow has not killed yet");
            s.assertClaimedBy(helper, s.hunter());
        });
    }

    @GameTest(template = T, batch = "kill_tnt", timeoutTicks = 100)
    public static void tntLitByPlayerCreditsThatPlayer(GameTestHelper helper) {
        Scene s = scene(helper);
        // Keep the hunter out of the blast: a dead hunter is only paid on respawn.
        Vec3 far = helper.absoluteVec(new Vec3(3.5, 1, 30));
        s.hunter().teleportTo(far.x, far.y, far.z);
        s.target().setHealth(1f);
        Vec3 p = s.target().position();
        PrimedTnt tnt = new PrimedTnt(helper.getLevel(), p.x, p.y, p.z, s.hunter());
        tnt.setFuse(1);
        helper.getLevel().addFreshEntity(tnt);
        helper.succeedWhen(() -> {
            helper.assertTrue(s.target().isDeadOrDying(), "TNT has not killed yet");
            s.assertClaimedBy(helper, s.hunter());
        });
    }

    @GameTest(template = T, batch = "marked_explosive", timeoutTicks = 100)
    public static void explosionOfMarkedOwnerlessEntityCreditsMarkedPlayer(GameTestHelper helper) {
        // Compat code marks ownerless munitions (CBC shells, More Shells bombs calling level.explode(shell, ...)).
        // The explosion's direct entity carries the mark: same path, with an ownerless TNT.
        Scene s = scene(helper);
        Vec3 far = helper.absoluteVec(new Vec3(3.5, 1, 30));
        s.hunter().teleportTo(far.x, far.y, far.z);
        s.target().setHealth(1f);
        Vec3 p = s.target().position();
        PrimedTnt tnt = new PrimedTnt(helper.getLevel(), p.x, p.y, p.z, null);
        dev.bloodcontracts.attribution.BCAttachments.mark(tnt, s.hunter().getUUID(), helper.getLevel().getGameTime());
        tnt.setFuse(1);
        helper.getLevel().addFreshEntity(tnt);
        helper.succeedWhen(() -> {
            helper.assertTrue(s.target().isDeadOrDying(), "TNT has not killed yet");
            s.assertClaimedBy(helper, s.hunter());
        });
    }

    @GameTest(template = T, batch = "unmarked_explosive", timeoutTicks = 100)
    public static void explosionOfOwnerlessEntityCreditsNobody(GameTestHelper helper) {
        Scene s = scene(helper);
        hit(s.target(), s.sources().playerAttack(s.hunter()), 1f);
        s.target().setHealth(1f);
        Vec3 far = helper.absoluteVec(new Vec3(3.5, 1, 30));
        s.hunter().teleportTo(far.x, far.y, far.z);
        Vec3 p = s.target().position();
        PrimedTnt tnt = new PrimedTnt(helper.getLevel(), p.x, p.y, p.z, null);
        tnt.setFuse(1);
        helper.getLevel().addFreshEntity(tnt);
        helper.succeedWhen(() -> {
            helper.assertTrue(s.target().isDeadOrDying(), "TNT has not killed yet");
            s.assertStillActive(helper);
        });
    }

    @GameTest(template = T, batch = "void_in_window", timeoutTicks = 100)
    public static void hitThenVoidWithinWindowCreditsHunter(GameTestHelper helper) {
        Scene s = scene(helper);
        hit(s.target(), s.sources().playerAttack(s.hunter()), 1f);
        helper.runAfterDelay(20, () -> {
            hit(s.target(), s.sources().fellOutOfWorld(), 1000f);
            helper.assertTrue(s.target().isDeadOrDying(), "void did not kill");
            s.assertClaimedBy(helper, s.hunter());
            helper.succeed();
        });
    }

    @GameTest(template = T, batch = "void_out_of_window", timeoutTicks = 100)
    public static void hitThenVoidOutsideWindowCreditsNobody(GameTestHelper helper) {
        Scene s = scene(helper);
        BCConfig.ATTRIBUTION_WINDOW_TICKS.set(20);
        hit(s.target(), s.sources().playerAttack(s.hunter()), 1f);
        helper.runAfterDelay(30, () -> {
            hit(s.target(), s.sources().fellOutOfWorld(), 1000f);
            helper.assertTrue(s.target().isDeadOrDying(), "void did not kill");
            s.assertStillActive(helper);
            helper.succeed();
        });
    }

    @GameTest(template = T, batch = "two_attackers", timeoutTicks = 100)
    public static void twoAttackersMostRecentWins(GameTestHelper helper) {
        Scene s = scene(helper);
        ServerPlayer second = TestPlayers.create(helper, "hunter2", new Vec3(3.5, 1, 4.5));
        hit(s.target(), s.sources().playerAttack(s.hunter()), 1f);
        helper.runAfterDelay(5, () -> hit(s.target(), s.sources().playerAttack(second), 1f));
        helper.runAfterDelay(10, () -> {
            hit(s.target(), s.sources().fellOutOfWorld(), 1000f);
            s.assertClaimedBy(helper, second);
            helper.assertTrue(TestSupport.countInInventory(s.hunter(), s.marker()) == 0, "earlier attacker must get nothing");
            helper.succeed();
        });
    }

    @GameTest(template = T, batch = "env_lava")
    public static void lavaWithoutPlayerCreditsNobody(GameTestHelper helper) {
        Scene s = scene(helper);
        hit(s.target(), s.sources().lava(), 1000f);
        helper.assertTrue(s.target().isDeadOrDying(), "lava did not kill");
        s.assertStillActive(helper);
        helper.succeed();
    }

    @GameTest(template = T, batch = "env_fall")
    public static void fallWithoutPlayerCreditsNobody(GameTestHelper helper) {
        Scene s = scene(helper);
        hit(s.target(), s.sources().fall(), 1000f);
        helper.assertTrue(s.target().isDeadOrDying(), "fall did not kill");
        s.assertStillActive(helper);
        helper.succeed();
    }

    @GameTest(template = T, batch = "env_drown")
    public static void drowningWithoutPlayerCreditsNobody(GameTestHelper helper) {
        Scene s = scene(helper);
        hit(s.target(), s.sources().drown(), 1000f);
        helper.assertTrue(s.target().isDeadOrDying(), "drowning did not kill");
        s.assertStillActive(helper);
        helper.succeed();
    }

    @GameTest(template = T, batch = "mob_kill")
    public static void mobKillAfterPlayerHitCreditsNobody(GameTestHelper helper) {
        Scene s = scene(helper);
        hit(s.target(), s.sources().playerAttack(s.hunter()), 1f);
        var zombie = helper.spawn(EntityType.ZOMBIE, 5, 1, 4);
        hit(s.target(), s.sources().mobAttack(zombie), 1000f);
        helper.assertTrue(s.target().isDeadOrDying(), "zombie did not kill");
        s.assertStillActive(helper);
        helper.succeed();
    }

    @GameTest(template = T, batch = "kill_command")
    public static void killCommandCreditsNobody(GameTestHelper helper) {
        Scene s = scene(helper);
        hit(s.target(), s.sources().playerAttack(s.hunter()), 1f);
        var server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "kill " + s.target().getGameProfile().getName());
        helper.assertTrue(s.target().isDeadOrDying(), "/kill did not kill");
        s.assertStillActive(helper);
        helper.succeed();
    }

    @GameTest(template = T, batch = "fake_player")
    public static void machineFakePlayerCreditsNobody(GameTestHelper helper) {
        Scene s = scene(helper);
        // Create's deployer attacks through a FakePlayer (possibly carrying its owner's UUID): never credited.
        var machine = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(s.hunter().getUUID(), "[Deployer]"));
        hit(s.target(), s.sources().playerAttack(machine), 1000f);
        helper.assertTrue(s.target().isDeadOrDying(), "fake player did not kill");
        s.assertStillActive(helper);
        helper.succeed();
    }

    @GameTest(template = T, batch = "pet_credit")
    public static void tamedWolfCreditsOwner(GameTestHelper helper) {
        Scene s = scene(helper);
        Wolf wolf = helper.spawn(EntityType.WOLF, 4, 1, 4);
        wolf.tame(s.hunter());
        hit(s.target(), s.sources().mobAttack(wolf), 1000f);
        helper.assertTrue(s.target().isDeadOrDying(), "wolf did not kill");
        s.assertClaimedBy(helper, s.hunter());
        helper.succeed();
    }

    @GameTest(template = T, batch = "pet_no_credit")
    public static void tamedWolfCreditsNobodyWhenDisabled(GameTestHelper helper) {
        Scene s = scene(helper);
        BCConfig.CREDIT_TAMED_PETS.set(false);
        Wolf wolf = helper.spawn(EntityType.WOLF, 4, 1, 4);
        wolf.tame(s.hunter());
        hit(s.target(), s.sources().mobAttack(wolf), 1000f);
        helper.assertTrue(s.target().isDeadOrDying(), "wolf did not kill");
        s.assertStillActive(helper);
        helper.succeed();
    }
}
