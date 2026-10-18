package dev.bloodcontracts;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(BloodContracts.MODID)
public class BloodContracts {
    public static final String MODID = "bloodcontracts";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BloodContracts(IEventBus modBus, ModContainer container) {
    }
}
