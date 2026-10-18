package dev.bloodcontracts;

import com.mojang.logging.LogUtils;
import dev.bloodcontracts.attribution.BCAttachments;
import dev.bloodcontracts.config.BCConfig;
import dev.bloodcontracts.core.BountyEvents;
import dev.bloodcontracts.menu.BCMenus;
import dev.bloodcontracts.network.BCNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(BloodContracts.MODID)
public class BloodContracts {
    public static final String MODID = "bloodcontracts";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BloodContracts(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, BCConfig.SPEC);
        BCAttachments.register(modBus);
        BCMenus.register(modBus);
        modBus.addListener(BCNetwork::register);
        BountyEvents.register(NeoForge.EVENT_BUS);
    }
}
