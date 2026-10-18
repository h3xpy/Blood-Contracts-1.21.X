package dev.bloodcontracts.menu;

import dev.bloodcontracts.BloodContracts;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BCMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, BloodContracts.MODID);

    public static final Supplier<MenuType<BountyCreateMenu>> BOUNTY_CREATE = MENUS.register("bounty_create",
            () -> new MenuType<>(BountyCreateMenu::new, FeatureFlags.VANILLA_SET));

    private BCMenus() {}

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
