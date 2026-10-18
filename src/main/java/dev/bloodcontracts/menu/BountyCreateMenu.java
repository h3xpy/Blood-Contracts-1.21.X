package dev.bloodcontracts.menu;

import dev.bloodcontracts.core.Bounty;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Bounty creation menu. The reward slots are real server-side slots: items are physically moved into them, and moved
 * into escrow by {@code BountyManager} on confirmation. Closing the menu without confirming gives them back.
 * The client never sends an item, only the target's UUID.
 * <p>
 * One reward slot by default; the "more items" button ({@link #BUTTON_TOGGLE_SLOTS}) reveals
 * {@link Bounty#MAX_REWARD_STACKS} slots. The expanded state lives on the server and is synced as a data slot.
 */
public class BountyCreateMenu extends AbstractContainerMenu {
    public static final int REWARD_SLOTS = Bounty.MAX_REWARD_STACKS;
    public static final int BUTTON_TOGGLE_SLOTS = 0;
    // Layout shared with BountyCreateScreen.
    public static final int IMAGE_HEIGHT = 246;
    public static final int REWARD_X = 8;
    public static final int REWARD_Y = 108;
    public static final int INVENTORY_Y = 164;

    private final Container rewards;
    private final DataSlot expanded = DataSlot.standalone();

    /** Client-side constructor. */
    public BountyCreateMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(REWARD_SLOTS));
    }

    public BountyCreateMenu(int containerId, Inventory playerInventory, Container rewards) {
        super(BCMenus.BOUNTY_CREATE.get(), containerId);
        checkContainerSize(rewards, REWARD_SLOTS);
        this.rewards = rewards;
        for (int i = 0; i < REWARD_SLOTS; i++) {
            addSlot(new RewardSlot(rewards, i, REWARD_X + i * 18, REWARD_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
        addDataSlot(expanded);
    }

    public static MenuProvider provider() {
        return new SimpleMenuProvider((id, inv, player) -> new BountyCreateMenu(id, inv, new SimpleContainer(REWARD_SLOTS)),
                Component.translatable("bloodcontracts.gui.create.title"));
    }

    public Container rewardContainer() {
        return rewards;
    }

    public boolean isExpanded() {
        return expanded.get() != 0;
    }

    /** Slots whose content becomes the reward when the bounty is signed. */
    public List<Integer> activeRewardSlots() {
        return isExpanded() ? IntStream.range(0, REWARD_SLOTS).boxed().toList() : List.of(0);
    }

    public boolean hasAnyReward() {
        return activeRewardSlots().stream().anyMatch(i -> !rewards.getItem(i).isEmpty());
    }

    /** Folding back is refused while an extra slot still holds an item: nothing is ever hidden. */
    public boolean canCollapse() {
        return IntStream.range(1, REWARD_SLOTS).allMatch(i -> rewards.getItem(i).isEmpty());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_TOGGLE_SLOTS) {
            return false;
        }
        if (isExpanded()) {
            if (!canCollapse()) {
                return false;
            }
            expanded.set(0);
        } else {
            expanded.set(1);
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < REWARD_SLOTS) {
            if (!moveItemStackTo(stack, REWARD_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, isExpanded() ? REWARD_SLOTS : 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Not signed (or signing refused): the items go back to the player, or at their feet.
        if (!player.level().isClientSide()) {
            clearContainer(player, rewards);
        }
    }

    /** Reward slot; the extra ones only exist while the menu is expanded. */
    private class RewardSlot extends Slot {
        RewardSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return getContainerSlot() == 0 || isExpanded();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isActive();
        }
    }
}
