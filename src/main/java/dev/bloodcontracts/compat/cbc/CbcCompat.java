package dev.bloodcontracts.compat.cbc;

import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.attribution.BCAttachments;
import dev.bloodcontracts.attribution.ResponsibleResolver;
import dev.bloodcontracts.config.BCConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import rbasamoyai.createbigcannons.cannon_control.ControlPitchContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;

/**
 * Create Big Cannons attribution. Only loaded when CBC is present. See {@code docs/cbc-attribution.md}.
 * <p>
 * CBC projectiles have no owner and hurt entities with a {@code CannonDamageSource} that carries no entity at all,
 * always from inside the projectile's (or burst's) own tick. So:
 * <ol>
 *   <li>Operators: a right-click on a cannon mount or on a block next to it (lever, button, hand crank) records
 *       that player for the mount. A player seated on the cannon is its operator.</li>
 *   <li>Firing: when a CBC projectile joins the level, the cannon that fired it (the contraption ticking right
 *       now, otherwise the one whose bounds contain the spawn point) gives the operator, if they interacted within
 *       {@code cannonOperatorWindowTicks}. The projectile is marked with {@link BCAttachments#RESPONSIBLE}.
 *       Anything a marked CBC entity spawns while ticking (shrapnel, grapeshot, flak bursts) inherits the mark.</li>
 *   <li>Damage: an entity-less CBC damage source is credited to the mark of the CBC entity currently ticking.</li>
 * </ol>
 * Any doubt (no operator, stale interaction, several candidate cannons with different operators) gives no mark.
 * No mixin is used.
 */
public final class CbcCompat {
    public static final String CBC = "createbigcannons";
    /** "Next to the mount": right-clicks within this Chebyshev distance of a mount count as operating it. */
    public static final int MOUNT_REACH = 1;
    /** A shell spawns at the muzzle, just outside the contraption bounds. */
    private static final double MUZZLE_MARGIN = 2.0;

    private static final CannonOperatorTracker TRACKER = new CannonOperatorTracker();
    private static final Deque<Entity> TICKING = new ArrayDeque<>();
    private static final Map<EntityType<?>, Boolean> IS_CBC = new IdentityHashMap<>();

    private CbcCompat() {}

    public static void init(IEventBus gameBus) {
        gameBus.addListener(CbcCompat::onRightClickBlock);
        gameBus.addListener(EventPriority.HIGHEST, CbcCompat::onEntityTickPre);
        gameBus.addListener(EventPriority.LOWEST, CbcCompat::onEntityTickPost);
        gameBus.addListener(CbcCompat::onEntityJoin);
        gameBus.addListener(CbcCompat::onServerTick);
        ResponsibleResolver.registerCompat(CbcCompat::resolve);
        BloodContracts.LOGGER.info("Blood Contracts: Create Big Cannons attribution enabled");
    }

    @VisibleForTesting
    public static CannonOperatorTracker tracker() {
        return TRACKER;
    }

    // ---------------------------------------------------------------- operators

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level && !(event.getEntity() instanceof FakePlayer)) {
            recordInteraction(level, event.getPos(), event.getEntity());
        }
    }

    /** Records {@code player} as operator of every cannon mount at or next to {@code clicked}. */
    @VisibleForTesting
    public static void recordInteraction(ServerLevel level, BlockPos clicked, Player player) {
        for (BlockPos pos : BlockPos.betweenClosed(clicked.offset(-MOUNT_REACH, -MOUNT_REACH, -MOUNT_REACH),
                clicked.offset(MOUNT_REACH, MOUNT_REACH, MOUNT_REACH))) {
            if (level.getBlockEntity(pos) instanceof ControlPitchContraption) {
                TRACKER.record(GlobalPos.of(level.dimension(), pos.immutable()), player.getUUID(), level.getGameTime());
                debug("player {} operates cannon mount {}", player.getUUID(), pos);
            }
        }
    }

    /** The player operating {@code cannon} right now: seated, or recent interaction with its mount. */
    static Optional<UUID> operatorOf(PitchOrientedContraptionEntity cannon, long now) {
        Optional<UUID> seated = seatedPlayer(cannon);
        if (seated.isPresent()) {
            return seated;
        }
        ControlPitchContraption controller = cannon.getController();
        if (controller instanceof BlockEntity be && be.getLevel() != null) {
            return TRACKER.operator(GlobalPos.of(be.getLevel().dimension(), be.getBlockPos()), now, BCConfig.cannonOperatorWindowTicks());
        }
        return Optional.empty();
    }

    private static Optional<UUID> seatedPlayer(PitchOrientedContraptionEntity cannon) {
        for (Entity e : List.of(cannon, cannon.getVehicle() != null ? cannon.getVehicle() : cannon)) {
            if (e.getControllingPassenger() instanceof Player p && !(p instanceof FakePlayer)) {
                return Optional.of(p.getUUID());
            }
            for (Entity passenger : e.getPassengers()) {
                if (passenger instanceof Player p && !(p instanceof FakePlayer)) {
                    return Optional.of(p.getUUID());
                }
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- tick context

    private static boolean isCbc(Entity entity) {
        return IS_CBC.computeIfAbsent(entity.getType(), t -> CBC.equals(BuiltInRegistries.ENTITY_TYPE.getKey(t).getNamespace()));
    }

    private static void onEntityTickPre(EntityTickEvent.Pre event) {
        Entity e = event.getEntity();
        if (!e.level().isClientSide() && isCbc(e)) {
            TICKING.push(e);
        }
    }

    private static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!TICKING.isEmpty() && TICKING.peek() == event.getEntity()) {
            TICKING.pop();
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        TICKING.clear();
        long now = event.getServer().overworld().getGameTime();
        if (now % 200 == 0) {
            TRACKER.purge(now, BCConfig.cannonOperatorWindowTicks());
        }
    }

    private static @Nullable Entity ticking() {
        return TICKING.peek();
    }

    // ---------------------------------------------------------------- marking

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && !event.loadedFromDisk()) {
            onCbcEntitySpawned(level, event.getEntity());
        }
    }

    @VisibleForTesting
    public static void onCbcEntitySpawned(ServerLevel level, Entity entity) {
        if (!isCbc(entity) || entity instanceof PitchOrientedContraptionEntity || BCAttachments.responsibleOf(entity).isPresent()) {
            return;
        }
        long now = level.getGameTime();
        // Fragments and bursts spawned by a marked shell inherit its mark.
        Entity parent = ticking();
        if (parent != null && !(parent instanceof PitchOrientedContraptionEntity)) {
            BCAttachments.responsibleOf(parent).ifPresent(p -> mark(entity, p, now, "inherited from " + parent.getUUID()));
            return;
        }
        if (!(entity instanceof AbstractCannonProjectile)) {
            return;
        }
        List<PitchOrientedContraptionEntity> cannons = new ArrayList<>();
        if (parent instanceof PitchOrientedContraptionEntity firing) {
            cannons.add(firing); // autocannons fire from their own tick: exact
        } else {
            AABB around = new AABB(entity.position(), entity.position()).inflate(MUZZLE_MARGIN);
            cannons.addAll(level.getEntitiesOfClass(PitchOrientedContraptionEntity.class, around));
        }
        Optional<UUID> operator = firedBy(cannons, now);
        if (operator.isPresent()) {
            mark(entity, operator.get(), now, "fired by " + cannons.size() + " candidate cannon(s)");
        } else {
            debug("{} {} fired without a reliable operator ({} candidate cannon(s))",
                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), entity.getUUID(), cannons.size());
        }
    }

    /** The common operator of the candidate cannons, or nothing if there is none or they disagree. */
    @VisibleForTesting
    public static Optional<UUID> firedBy(List<PitchOrientedContraptionEntity> candidates, long now) {
        UUID operator = null;
        for (PitchOrientedContraptionEntity cannon : candidates) {
            Optional<UUID> op = operatorOf(cannon, now);
            if (op.isEmpty() || (operator != null && !operator.equals(op.get()))) {
                return Optional.empty();
            }
            operator = op.get();
        }
        return Optional.ofNullable(operator);
    }

    private static void mark(Entity entity, UUID player, long now, String why) {
        BCAttachments.mark(entity, player, now);
        debug("marked {} {} for player {} ({})", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), entity.getUUID(), player, why);
    }

    // ---------------------------------------------------------------- damage

    /** Entity-less CBC damage is credited to the mark of the CBC entity ticking right now. */
    static Optional<UUID> resolve(DamageSource source) {
        if (source.getEntity() != null || source.getDirectEntity() != null) {
            return Optional.empty();
        }
        boolean cbcDamage = source.typeHolder().unwrapKey().map(k -> CBC.equals(k.location().getNamespace())).orElse(false);
        Entity current = ticking();
        if (!cbcDamage || current == null) {
            return Optional.empty();
        }
        return BCAttachments.responsibleOf(current);
    }

    private static void debug(String message, Object... args) {
        if (BCConfig.debug()) {
            BloodContracts.LOGGER.info("[bounty/cbc] " + message, args);
        }
    }
}
