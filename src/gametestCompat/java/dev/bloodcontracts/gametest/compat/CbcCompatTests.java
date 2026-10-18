package dev.bloodcontracts.gametest.compat;

import static dev.bloodcontracts.gametest.TestSupport.assertConserved;
import static dev.bloodcontracts.gametest.TestSupport.countInInventory;
import static dev.bloodcontracts.gametest.TestSupport.hold;
import static dev.bloodcontracts.gametest.TestSupport.marked;
import static dev.bloodcontracts.gametest.TestSupport.placeFromHand;

import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.attribution.BCAttachments;
import dev.bloodcontracts.compat.cbc.CbcCompat;
import dev.bloodcontracts.config.BCConfig;
import dev.bloodcontracts.core.Bounty;
import dev.bloodcontracts.core.BountyManager;
import dev.bloodcontracts.core.BountyState;
import dev.bloodcontracts.gametest.BloodContractsGameTests;
import dev.bloodcontracts.gametest.TestPlayers;
import dev.bloodcontracts.gametest.TestSupport;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlock;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.carriage.CannonCarriageEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.index.CBCEntityTypes;
import rbasamoyai.createbigcannons.cannons.big_cannons.IBigCannonBlockEntity;
import rbasamoyai.createbigcannons.config.CBCConfigs;
import rbasamoyai.createbigcannons.index.CBCBlocks;
import rbasamoyai.createbigcannons.index.CBCItems;
import rbasamoyai.createbigcannons.munitions.big_cannon.BigCannonMunitionBlock;

/**
 * Create Big Cannons attribution with a real cannon: mount, cast iron breech and barrels, powder charge and shell
 * loaded through CBC's handloading API, fired by redstone through the mount's scheduled block tick.
 * Layout (relative): mount at (1,0,1) facing east, cannon two blocks above it along +X, target downrange.
 */
@GameTestHolder(BloodContracts.MODID)
@PrefixGameTestTemplate(false)
public class CbcCompatTests {
    private static final String T = BloodContractsGameTests.TEMPLATE;
    /**
     * 20 blocks long firing range with an obsidian backstop: shells must never leave their own test area, because
     * Ritchie's Projectile Library force-loads the chunks a shell crosses and later releases that force-load, which would
     * also release the one the GameTest framework puts on a neighbouring test area (that test would then wait forever).
     */
    private static final String RANGE = "cannon_range";
    private static final int BACKSTOP_X = 15;
    private static final BlockPos MOUNT = new BlockPos(1, 0, 1);
    private static final BlockPos FIRE_SIDE = MOUNT.east();
    private static final BlockPos ASSEMBLY_SIDE = MOUNT.west();

    record Range(BountyManager manager, ServerPlayer sponsor, ServerPlayer target, ServerPlayer operator, Bounty bounty, UUID marker) {}

    /** Builds and assembles a loaded cannon, places a target downrange with a bounty on it. */
    private static Range setUp(GameTestHelper helper, BlockState shell, boolean impactFuze) {
        return setUp(helper, shell, impactFuze, new Vec3(9.5, MOUNT.getY() + 2, 1.5));
    }

    private static Range setUp(GameTestHelper helper, BlockState shell, boolean impactFuze, Vec3 targetPos) {
        BountyManager manager = TestSupport.reset(helper);
        helper.assertTrue(ModList.get().isLoaded(CbcCompat.CBC), "CBC not loaded in the compat run");
        CbcCompat.tracker().clear();

        // Cannon blocks: breech then barrels along +X, two blocks above the mount.
        int y = MOUNT.getY() + 2;
        BlockPos breech = new BlockPos(MOUNT.getX(), y, MOUNT.getZ());
        helper.setBlock(breech, CBCBlocks.CAST_IRON_SLIDING_BREECH.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        for (int i = 1; i <= 4; i++) {
            helper.setBlock(breech.east(i), CBCBlocks.CAST_IRON_CANNON_BARREL.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        }
        // CBC connects neighbouring cannon blocks when a player places them; do the same by hand.
        for (int i = 0; i < 4; i++) {
            ((IBigCannonBlockEntity) helper.getBlockEntity(breech.east(i))).cannonBehavior().setConnectedFace(Direction.EAST, true);
            ((IBigCannonBlockEntity) helper.getBlockEntity(breech.east(i + 1))).cannonBehavior().setConnectedFace(Direction.WEST, true);
        }
        // Properly loaded: two charges, the shell, one empty barrel (cast iron squibs below 2 charges per barrel).
        load(helper, breech.east(1), CBCBlocks.POWDER_CHARGE.getDefaultState(), new ItemStack(CBCBlocks.POWDER_CHARGE.get()), false);
        load(helper, breech.east(2), CBCBlocks.POWDER_CHARGE.getDefaultState(), new ItemStack(CBCBlocks.POWDER_CHARGE.get()), false);
        load(helper, breech.east(3), shell, new ItemStack(shell.getBlock()), impactFuze);
        // CBC's random misfires are its own business, not ours: keep the cannon deterministic.
        CBCConfigs.server().failure.disableAllFailure.set(true);

        // Backstop: three layers of obsidian across the whole line of fire.
        for (int bx = BACKSTOP_X; bx < BACKSTOP_X + 3; bx++) {
            for (int by = 0; by < 8; by++) {
                for (int bz = 0; bz < 8; bz++) {
                    helper.setBlock(new BlockPos(bx, by, bz), Blocks.OBSIDIAN);
                }
            }
        }
        helper.setBlock(MOUNT, CBCBlocks.CANNON_MOUNT.getDefaultState().setValue(CannonMountBlock.HORIZONTAL_FACING, Direction.EAST));
        // Assemble through the assembly side, as a redstone signal would.
        helper.setBlock(ASSEMBLY_SIDE, Blocks.REDSTONE_BLOCK);
        mount(helper).onRedstoneUpdate(true, false, false, false, 0);
        if (cannon(helper) == null) {
            Object error = net.neoforged.fml.util.ObfuscationReflectionHelper.getPrivateValue(CannonMountBlockEntity.class, mount(helper), "lastException");
            helper.fail("cannon did not assemble: " + (error instanceof com.simibubi.create.content.contraptions.AssemblyException ae ? ae.component.getString() : error) + " / blocks: " + helper.getBlockState(breech) + ", " + helper.getBlockState(breech.east()));
        }

        ServerPlayer sponsor = TestPlayers.create(helper, "sponsor", new Vec3(0.5, 0, 6.5));
        ServerPlayer operator = TestPlayers.create(helper, "gunner", new Vec3(0.5, 0, 4.5));
        ServerPlayer target = TestPlayers.create(helper, "target", targetPos);
        target.setHealth(1f);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 3, marker));
        Bounty b = placeFromHand(manager, sponsor, target).value();
        helper.assertTrue(b != null, "setup: bounty creation failed");
        return new Range(manager, sponsor, target, operator, b, marker);
    }

    private static void load(GameTestHelper helper, BlockPos barrel, BlockState munition, ItemStack stack, boolean impactFuze) {
        var registries = helper.getLevel().registryAccess();
        StructureBlockInfo info = ((BigCannonMunitionBlock) munition.getBlock())
                .getHandloadingInfo(stack, helper.absolutePos(barrel), Direction.EAST, registries);
        if (impactFuze && info.nbt() != null) {
            info.nbt().put("Fuze", new ItemStack(CBCItems.IMPACT_FUZE.get()).saveOptional(registries));
        }
        IBigCannonBlockEntity be = (IBigCannonBlockEntity) helper.getBlockEntity(barrel);
        be.cannonBehavior().loadBlock(info);
    }

    private static CannonMountBlockEntity mount(GameTestHelper helper) {
        return (CannonMountBlockEntity) helper.getBlockEntity(MOUNT);
    }

    private static PitchOrientedContraptionEntity cannon(GameTestHelper helper) {
        List<PitchOrientedContraptionEntity> found = helper.getLevel().getEntitiesOfClass(PitchOrientedContraptionEntity.class,
                helper.getBounds().inflate(4));
        return found.isEmpty() ? null : found.get(0);
    }

    /** The operator flips a lever on the mount's fire side: the same observable a real click produces. */
    private static void pullFireLever(GameTestHelper helper, ServerPlayer operator) {
        helper.setBlock(FIRE_SIDE, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
        BlockPos abs = helper.absolutePos(FIRE_SIDE);
        CommonHooks.onRightClickBlock(operator, InteractionHand.MAIN_HAND, abs,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        helper.pullLever(FIRE_SIDE);
    }

    private static void cleanUp(GameTestHelper helper) {
        PitchOrientedContraptionEntity c = cannon(helper);
        if (c != null) {
            c.discard();
        }
        helper.getLevel().getEntitiesOfClass(rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile.class,
                helper.getBounds().inflate(32)).forEach(net.minecraft.world.entity.Entity::discard);
    }

    @GameTest(template = RANGE, batch = "cbc_operator", timeoutTicks = 100)
    public static void shotAfterRecentInteractionCreditsOperator(GameTestHelper helper) {
        Range r = setUp(helper, CBCBlocks.SOLID_SHOT.getDefaultState(), false);
        pullFireLever(helper, r.operator());
        helper.succeedWhen(() -> {
            helper.assertTrue(r.target().isDeadOrDying(), "shot has not killed the target yet");
            Bounty b = r.manager().findAny(r.bounty().id()).orElseThrow();
            helper.assertTrue(b.state() == BountyState.CLAIMED, "not claimed: " + b.state());
            helper.assertTrue(r.operator().getUUID().equals(b.claimedBy()), "credited to " + b.claimedBy() + " instead of the operator");
            helper.assertTrue(TestSupport.countOwed(r.manager(), r.operator(), r.marker()) == 3, "operator not paid");
            assertConserved(helper, r.manager(), r.marker(), 3);
            cleanUp(helper);
        });
    }

    @GameTest(template = RANGE, batch = "cbc_redstone", timeoutTicks = 100)
    public static void redstoneShotWithoutInteractionCreditsNobody(GameTestHelper helper) {
        Range r = setUp(helper, CBCBlocks.SOLID_SHOT.getDefaultState(), false);
        // Fired by plain redstone: nobody touched the cannon.
        helper.setBlock(FIRE_SIDE, Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(() -> {
            helper.assertTrue(r.target().isDeadOrDying(), "shot has not killed the target yet");
            helper.assertTrue(r.manager().findActive(r.bounty().id()).isPresent(), "bounty claimed without an operator");
            helper.assertTrue(r.manager().data().deliveries().isEmpty(), "unexpected delivery");
            assertConserved(helper, r.manager(), r.marker(), 3);
            cleanUp(helper);
        });
    }

    @GameTest(template = RANGE, batch = "cbc_stale", timeoutTicks = 120)
    public static void staleInteractionCreditsNobody(GameTestHelper helper) {
        Range r = setUp(helper, CBCBlocks.SOLID_SHOT.getDefaultState(), false);
        BCConfig.CANNON_OPERATOR_WINDOW_TICKS.set(10);
        BlockPos abs = helper.absolutePos(MOUNT);
        CommonHooks.onRightClickBlock(r.operator(), InteractionHand.MAIN_HAND, abs, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        // Fire long after the interaction (window is 10 ticks).
        helper.runAfterDelay(30, () -> helper.setBlock(FIRE_SIDE, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(31, () -> helper.succeedWhen(() -> {
            helper.assertTrue(r.target().isDeadOrDying(), "shot has not killed the target yet");
            helper.assertTrue(r.manager().findActive(r.bounty().id()).isPresent(), "stale interaction was credited");
            assertConserved(helper, r.manager(), r.marker(), 3);
            cleanUp(helper);
        }));
    }

    @GameTest(template = RANGE, batch = "cbc_explosion", timeoutTicks = 100)
    public static void shellExplosionCreditsOperator(GameTestHelper helper) {
        // The target stands off the line of fire; a wall stops the shell, only the blast can reach the target.
        Range r = setUp(helper, CBCBlocks.HE_SHELL.getDefaultState(), true, new Vec3(8.5, MOUNT.getY() + 2, 3.5));
        for (int dy = 0; dy <= 4; dy++) {
            for (int dz = 0; dz <= 2; dz++) {
                helper.setBlock(new BlockPos(9, dy, dz), Blocks.OBSIDIAN);
            }
        }
        pullFireLever(helper, r.operator());
        helper.succeedWhen(() -> {
            helper.assertTrue(r.target().isDeadOrDying(), "shell has not killed the target yet");
            Bounty b = r.manager().findAny(r.bounty().id()).orElseThrow();
            helper.assertTrue(b.state() == BountyState.CLAIMED, "not claimed: " + b.state());
            helper.assertTrue(r.operator().getUUID().equals(b.claimedBy()), "credited to " + b.claimedBy());
            helper.assertTrue(r.target().getLastDamageSource() != null && r.target().getLastDamageSource().getEntity() == null,
                    "expected an entity-less CBC blast, got " + r.target().getLastDamageSource());
            cleanUp(helper);
        });
    }

    @GameTest(template = RANGE, batch = "cbc_seated")
    public static void playerOnCarriageIsOperator(GameTestHelper helper) {
        // A mounted big cannon has no seat (Create refuses passengers without one): seated operators ride a cannon
        // carriage. Closest level: a real carriage carrying the real cannon and the player, operator lookup called
        // directly (no shot: a carriage-borne cannon would also need CBC's carriage aiming).
        Range r = setUp(helper, CBCBlocks.SOLID_SHOT.getDefaultState(), false);
        PitchOrientedContraptionEntity cannon = cannon(helper);
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(CbcCompat.firedBy(List.of(cannon), now).isEmpty(), "nobody operates the cannon yet");

        CannonCarriageEntity carriage = CBCEntityTypes.CANNON_CARRIAGE.get().create(helper.getLevel());
        carriage.moveTo(cannon.position());
        helper.getLevel().addFreshEntity(carriage);
        helper.assertTrue(cannon.startRiding(carriage, true), "cannon could not ride the carriage");
        helper.assertTrue(r.operator().startRiding(carriage, true), "player could not ride the carriage");

        helper.assertTrue(CbcCompat.firedBy(List.of(cannon), now).filter(r.operator().getUUID()::equals).isPresent(),
                "player on the carriage is not the operator");
        r.operator().stopRiding();
        cannon.stopRiding();
        carriage.discard();
        cleanUp(helper);
        helper.succeed();
    }

    // ---------------------------------------------------------------- CBC Enchanced Shells ("More Shells") addon

    private static final String MORE_SHELLS = "cbc_enhanced_shells";

    private static BlockState moreShellsBlock(GameTestHelper helper, String id) {
        helper.assertTrue(ModList.get().isLoaded(MORE_SHELLS), "More Shells not loaded in the compat run");
        var key = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MORE_SHELLS, id);
        helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(key), "missing block " + key);
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(key).defaultBlockState();
    }

    @GameTest(template = RANGE, batch = "cbces_he_mk", timeoutTicks = 100)
    public static void moreShellsHeShellCreditsOperator(GameTestHelper helper) {
        // Addon shell (its own entity namespace, extends CBC's HE shell) fired from a real cannon, blast behind a wall.
        Range r = setUp(helper, moreShellsBlock(helper, "he_shell_mk1"), true, new Vec3(8.5, MOUNT.getY() + 2, 3.5));
        for (int dy = 0; dy <= 4; dy++) {
            for (int dz = 0; dz <= 2; dz++) {
                helper.setBlock(new BlockPos(9, dy, dz), Blocks.OBSIDIAN);
            }
        }
        pullFireLever(helper, r.operator());
        helper.succeedWhen(() -> {
            helper.assertTrue(r.target().isDeadOrDying(), "More Shells HE shell has not killed the target yet");
            Bounty b = r.manager().findAny(r.bounty().id()).orElseThrow();
            helper.assertTrue(r.operator().getUUID().equals(b.claimedBy()), "credited to " + b.claimedBy() + " (" + b.state() + ")");
            cleanUp(helper);
        });
    }

    @GameTest(template = T, batch = "cbces_rocket_base")
    public static void rocketBaseLauncherCreditsOperator(GameTestHelper helper) {
        // Closest level: a real rocket base with a lever clicked next to it, and a real rocket entity appearing on it
        // (the launch itself needs a full rocket stack). A rocket appearing on an untouched base is not marked.
        TestSupport.reset(helper);
        CbcCompat.tracker().clear();
        ServerPlayer operator = TestPlayers.create(helper, "gunner", new Vec3(0.5, 0, 4.5));
        BlockPos base = new BlockPos(2, 0, 2);
        BlockPos untouchedBase = new BlockPos(6, 0, 6);
        helper.setBlock(base, moreShellsBlock(helper, "rocket_base"));
        helper.setBlock(untouchedBase, moreShellsBlock(helper, "rocket_base"));
        BlockPos lever = base.east();
        helper.setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
        BlockPos absLever = helper.absolutePos(lever);
        CommonHooks.onRightClickBlock(operator, InteractionHand.MAIN_HAND, absLever, new BlockHitResult(Vec3.atCenterOf(absLever), Direction.UP, absLever, false));

        var rocketType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MORE_SHELLS, "rocket"));
        var rocket = rocketType.create(helper.getLevel());
        helper.assertTrue(rocket instanceof rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile, "rocket is not a CBC projectile");
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(base).add(0, 1, 0));
        rocket.moveTo(at.x, at.y, at.z);
        rocket.setNoGravity(true);
        helper.getLevel().addFreshEntity(rocket);
        helper.assertTrue(BCAttachments.responsibleOf(rocket).filter(operator.getUUID()::equals).isPresent(), "rocket not marked for the operator");

        var stray = rocketType.create(helper.getLevel());
        Vec3 at2 = helper.absoluteVec(Vec3.atBottomCenterOf(untouchedBase).add(0, 1, 0));
        stray.moveTo(at2.x, at2.y, at2.z);
        helper.getLevel().addFreshEntity(stray);
        helper.assertTrue(BCAttachments.responsibleOf(stray).isEmpty(), "rocket from an untouched launcher was marked");
        rocket.discard();
        stray.discard();
        helper.succeed();
    }

    @GameTest(template = T, batch = "cbc_mark_persists")
    public static void markSurvivesSerialization(GameTestHelper helper) {
        TestSupport.reset(helper);
        ServerPlayer p = TestPlayers.create(helper, "gunner", new Vec3(0.5, 0, 0.5));
        var shot = rbasamoyai.createbigcannons.index.CBCEntityTypes.SHOT.get().create(helper.getLevel());
        BCAttachments.mark(shot, p.getUUID(), helper.getLevel().getGameTime());
        var tag = shot.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        var copy = rbasamoyai.createbigcannons.index.CBCEntityTypes.SHOT.get().create(helper.getLevel());
        copy.load(tag);
        helper.assertTrue(BCAttachments.responsibleOf(copy).filter(p.getUUID()::equals).isPresent(), "mark lost on save/load");
        helper.succeed();
    }
}
