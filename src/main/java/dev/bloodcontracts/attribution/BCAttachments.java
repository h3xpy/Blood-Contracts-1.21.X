package dev.bloodcontracts.attribution;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.bloodcontracts.BloodContracts;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

/** Data attachments. {@link #RESPONSIBLE} marks an entity (a cannon shell...) with the player responsible for it. */
public final class BCAttachments {
    public record Responsible(UUID player, long markedAt) {
        static final Responsible NONE = new Responsible(new UUID(0L, 0L), 0L);
        static final Codec<Responsible> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(Responsible::player),
                Codec.LONG.fieldOf("markedAt").forGetter(Responsible::markedAt)
        ).apply(i, Responsible::new));
    }

    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BloodContracts.MODID);

    public static final Supplier<AttachmentType<Responsible>> RESPONSIBLE = TYPES.register("responsible",
            () -> AttachmentType.builder(() -> Responsible.NONE).serialize(Responsible.CODEC).build());

    private BCAttachments() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    public static void mark(Entity entity, UUID player, long gameTime) {
        entity.setData(RESPONSIBLE, new Responsible(player, gameTime));
    }

    public static Optional<UUID> responsibleOf(@Nullable Entity entity) {
        if (entity == null || !entity.hasData(RESPONSIBLE)) {
            return Optional.empty();
        }
        Responsible r = entity.getData(RESPONSIBLE);
        return r.equals(Responsible.NONE) ? Optional.empty() : Optional.of(r.player());
    }
}
