package dev.bloodcontracts.attribution;

import dev.bloodcontracts.BloodContracts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Resolves a {@link DamageSource} to the player responsible for it.
 * <ol>
 *   <li>{@code source.getEntity()} is a {@link FakePlayer} (Create deployer, other machines): nobody. A machine's
 *       owner is never assumed responsible for its victims.</li>
 *   <li>{@code source.getEntity()} is a player: that player (covers arrows, TNT lit by a player, thrown potions...).
 *       {@code getDirectEntity()} is never used as the responsible: it is the arrow or the TNT.</li>
 *   <li>{@code source.getEntity()} is an {@link OwnableEntity} owned by a player: the owner, if {@code creditTamedPets}.</li>
 *   <li>The direct or causing entity carries a {@link BCAttachments#RESPONSIBLE} mark (set by compat code): that player.</li>
 *   <li>Registered compat resolvers.</li>
 *   <li>Otherwise: nobody.</li>
 * </ol>
 */
public final class ResponsibleResolver {
    /** Damage types after which recent player damage is credited (void, fall, lava...). Data-driven, extendable. */
    public static final TagKey<DamageType> ENVIRONMENTAL = TagKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(BloodContracts.MODID, "environmental"));
    /** Damage types that never give an attribution ({@code /kill}). */
    public static final TagKey<DamageType> NEVER_ATTRIBUTED = TagKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(BloodContracts.MODID, "never_attributed"));

    /** Optional-mod hook: returns the responsible player for sources the vanilla rules cannot resolve. */
    @FunctionalInterface
    public interface CompatResolver {
        Optional<UUID> resolve(DamageSource source);
    }

    private static final List<CompatResolver> COMPAT = new CopyOnWriteArrayList<>();

    private ResponsibleResolver() {}

    public static void registerCompat(CompatResolver resolver) {
        COMPAT.add(resolver);
    }

    public static Optional<UUID> resolve(DamageSource source, boolean creditTamedPets) {
        Entity causing = source.getEntity();
        if (causing instanceof FakePlayer) {
            return Optional.empty();
        }
        if (causing instanceof Player player) {
            return Optional.of(player.getUUID());
        }
        if (causing instanceof OwnableEntity ownable) {
            if (!creditTamedPets) {
                return Optional.empty();
            }
            UUID owner = ownable.getOwnerUUID();
            LivingEntity ownerEntity = ownable.getOwner();
            // Owner offline: the UUID of a tamed animal's owner is always a player's.
            if (owner != null && (ownerEntity == null || ownerEntity instanceof Player)) {
                return Optional.of(owner);
            }
            return Optional.empty();
        }
        Optional<UUID> marked = BCAttachments.responsibleOf(source.getDirectEntity());
        if (marked.isEmpty()) {
            marked = BCAttachments.responsibleOf(causing);
        }
        if (marked.isPresent()) {
            return marked;
        }
        for (CompatResolver compat : COMPAT) {
            Optional<UUID> resolved = compat.resolve(source);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    public static KillAttributor.DeathCause classify(DamageSource source) {
        if (source.is(NEVER_ATTRIBUTED)) {
            return KillAttributor.DeathCause.COMMAND;
        }
        if (source.is(ENVIRONMENTAL)) {
            return KillAttributor.DeathCause.ENVIRONMENTAL;
        }
        return KillAttributor.DeathCause.OTHER;
    }
}
