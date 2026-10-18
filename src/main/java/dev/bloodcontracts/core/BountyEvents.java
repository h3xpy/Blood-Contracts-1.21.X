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
        gameBus.addListener(EventPriority.LOW, BountyEvents::onDeath);
        gameBus.addListener(BountyEvents::onLogin);
        gameBus.addListener(BountyEvents::onLogout);
        gameBus.addListener(BountyEvents::onServerTick);
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
            BountyManager.get(player.server).onPlayerLogout(player);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        BountyManager.get(event.getServer()).tick();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        BountyManager.release();
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        BountyCommands.register(event.getDispatcher());
    }
}
