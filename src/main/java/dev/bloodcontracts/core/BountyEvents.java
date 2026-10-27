package dev.bloodcontracts.core;

import dev.bloodcontracts.command.BountyCommands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Wires game events to {@link BountyManager}. Holds no logic of its own. */
public final class BountyEvents {
    private BountyEvents() {}

    public static void register(IEventBus gameBus) {
        gameBus.addListener(BountyEvents::onDamage);
        // Low priority: let other mods cancel the death (totems, revive mods...) first; cancelled deaths are not received.
        // Last, and only if no other mod cancelled the death (revive mods...): in doubt, no reward.
        gameBus.addListener(EventPriority.LOWEST, BountyEvents::onDeath);
        gameBus.addListener(BountyEvents::onLogin);
        gameBus.addListener(BountyEvents::onLogout);
        gameBus.addListener(BountyEvents::onServerTick);
        gameBus.addListener(BountyEvents::onServerStarted);
        gameBus.addListener(BountyEvents::onServerStopping);
        gameBus.addListener(BountyEvents::onRespawn);
        gameBus.addListener(BountyEvents::onClone);
        gameBus.addListener(BountyEvents::onDimensionChange);
        gameBus.addListener(BountyEvents::onServerStopped);
        gameBus.addListener(BountyEvents::onRegisterCommands);
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity().level().getServer() != null && !event.getEntity().level().isClientSide()) {
            BountyManager.get(event.getEntity().level().getServer()).onDamage(event.getEntity(), event.getSource(), event.getNewDamage());
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().getServer() != null && !event.getEntity().level().isClientSide()) {
            BountyManager.get(event.getEntity().level().getServer()).onDeath(event.getEntity(), event.getSource());
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BountyManager.get(player.server).onPlayerLogin(player);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Fired before the player data is saved; the menu itself is only closed afterwards, when the player is
            // already disconnected, and would then drop its items on the ground where they despawn.
            if (player.containerMenu instanceof dev.bloodcontracts.menu.BountyCreateMenu menu) {
                menu.returnRewards(player);
            }
            BountyManager.get(player.server).onPlayerLogout(player);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        BountyManager.get(event.getServer()).tick();
    }

    private static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        BountyManager.get(event.getServer()).onServerStarted();
    }

    private static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        BountyManager.get(event.getServer()).onServerStopping();
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BountyManager.get(player.server).onPlayerRespawnOrClone(player);
        }
    }

    private static void onClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BountyManager.get(player.server).onPlayerRespawnOrClone(player);
        }
    }

    private static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BountyManager.get(player.server).onPlayerRespawnOrClone(player);
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        BountyManager.release();
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        BountyCommands.register(event.getDispatcher());
    }
}
