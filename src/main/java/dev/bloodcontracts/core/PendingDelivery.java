package dev.bloodcontracts.core;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;

/**
 * An item that left the escrow and is owed to a player. It is deleted only once the item is in their
 * inventory (or dropped at their feet), so an offline player or a crash never loses it.
 */
public record PendingDelivery(UUID id, UUID recipient, ItemStack stack, UUID bountyId, Reason reason) {
    public enum Reason { REWARD, REFUND }

    public PendingDelivery {
        Objects.requireNonNull(id);
        Objects.requireNonNull(recipient);
        Objects.requireNonNull(bountyId);
        Objects.requireNonNull(reason);
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("A delivery must carry an item");
        }
    }
}
