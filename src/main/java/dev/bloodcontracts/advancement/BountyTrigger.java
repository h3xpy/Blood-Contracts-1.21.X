package dev.bloodcontracts.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.bloodcontracts.BloodContracts;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Advancement trigger {@code bloodcontracts:bounty}: {@code {"event": "placed|claimed|artillery|wanted", "min": n}}.
 * <ul>
 *   <li>{@code placed}: the player signed a bounty;</li>
 *   <li>{@code claimed}: the player collected bounties by a kill (value: how many at once);</li>
 *   <li>{@code artillery}: same, with a cannon shot (Create Big Cannons and addons);</li>
 *   <li>{@code wanted}: active bounties on the player's head (value: how many).</li>
 * </ul>
 */
public class BountyTrigger extends SimpleCriterionTrigger<BountyTrigger.Instance> {
    public enum Event implements StringRepresentable {
        PLACED, CLAIMED, ARTILLERY, WANTED;

        public static final Codec<Event> CODEC = StringRepresentable.fromEnum(Event::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, BloodContracts.MODID);
    public static final Supplier<BountyTrigger> BOUNTY = TRIGGERS.register("bounty", BountyTrigger::new);

    public static void register(IEventBus modBus) {
        TRIGGERS.register(modBus);
    }

    /** Fires the trigger; safe to call for any server player. */
    public static void fire(ServerPlayer player, Event event, int value) {
        BOUNTY.get().trigger(player, instance -> instance.event() == event && value >= instance.min().orElse(1));
    }

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public record Instance(Optional<ContextAwarePredicate> player, Event event, Optional<Integer> min) implements SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Event.CODEC.fieldOf("event").forGetter(Instance::event),
                Codec.INT.optionalFieldOf("min").forGetter(Instance::min)
        ).apply(i, Instance::new));
    }
}
