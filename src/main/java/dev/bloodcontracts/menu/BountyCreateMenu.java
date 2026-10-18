package dev.bloodcontracts.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Bounty creation menu. The reward slot is a real server-side slot: the item is physically moved into it, and moved
 * into escrow by {@code BountyManager} on confirmation. Closing the menu without confirming gives it back.
 * The client never sends an item, only the target's UUID.
 */
public class BountyCreateMenu extends AbstractContainerMenu {
    public static final int REWARD_SLOT = 0;
    // Layout shared with BountyCreateScreen (196 px tall "wanted poster").
    public static final int REWARD_X = 8;
    public static final int REWARD_Y = 84;
    public static final int INVENTORY_Y = 114;

    private final Container reward;

    /** Client-side constructor. */
    public BountyCreateMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(1));
    }

    public BountyCreateMenu(int containerId, Inventory playerInventory, Container reward) {
        super(BCMenus.BOUNTY_CREATE.get(), containerId);
        checkContainerSize(reward, 1);
        this.reward = reward;
        addSlot(new Slot(reward, REWARD_SLOT, REWARD_X, REWARD_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
    }

    public static MenuProvider provider() {
        return new SimpleMenuProvider((id, inv, player) -> new BountyCreateMenu(id, inv, new SimpleContainer(1)),
                Component.translatable("bloodcontracts.gui.create.title"));
    }

    public Container rewardContainer() {
        return reward;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == REWARD_SLOT) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, REWARD_SLOT, REWARD_SLOT + 1, false)) {
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
        // Not confirmed (or confirmation refused): the item goes back to the player, or at their feet.
        if (!player.level().isClientSide()) {
            clearContainer(player, reward);
        }
    }
}
