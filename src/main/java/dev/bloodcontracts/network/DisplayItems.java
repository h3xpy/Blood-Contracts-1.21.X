package dev.bloodcontracts.network;

import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Light copies of reward stacks for the views sent to clients. A reward can carry megabytes (a shulker box of written
 * books, a schematic, a mod's block entity data): sent as is, it would exceed the 1 MiB limit of a custom payload and
 * disconnect whoever opens the board. Views only need to draw the item, so they keep the components that change its
 * look or its short tooltip and drop the rest. The escrow, deliveries and the item actually handed out are untouched.
 */
public final class DisplayItems {
    /** Components kept for display: all small and bounded in survival. */
    private static final List<DataComponentType<?>> KEPT = List.of(
            DataComponents.CUSTOM_NAME, DataComponents.ITEM_NAME, DataComponents.RARITY,
            DataComponents.ENCHANTMENTS, DataComponents.STORED_ENCHANTMENTS, DataComponents.ENCHANTMENT_GLINT_OVERRIDE,
            DataComponents.DAMAGE, DataComponents.MAX_DAMAGE, DataComponents.UNBREAKABLE, DataComponents.MAX_STACK_SIZE,
            DataComponents.DYED_COLOR, DataComponents.POTION_CONTENTS, DataComponents.TRIM, DataComponents.PROFILE,
            DataComponents.BANNER_PATTERNS, DataComponents.BASE_COLOR, DataComponents.CUSTOM_MODEL_DATA,
            DataComponents.FIREWORK_EXPLOSION, DataComponents.FIREWORKS, DataComponents.INSTRUMENT,
            DataComponents.OMINOUS_BOTTLE_AMPLIFIER, DataComponents.HIDE_TOOLTIP, DataComponents.HIDE_ADDITIONAL_TOOLTIP);

    private DisplayItems() {}

    /** The same item and count, with only the display components that differ from the item's defaults. */
    public static ItemStack lite(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack lite = new ItemStack(stack.getItem(), stack.getCount());
        for (DataComponentType<?> type : KEPT) {
            copy(type, stack, lite);
        }
        return lite;
    }

    public static List<ItemStack> lite(List<ItemStack> stacks) {
        return stacks.stream().map(DisplayItems::lite).toList();
    }

    private static <T> void copy(DataComponentType<T> type, ItemStack from, ItemStack to) {
        if (from.getComponentsPatch().get(type) != null) {
            T value = from.get(type);
            if (value != null) {
                to.set(type, value);
            }
        }
    }
}
