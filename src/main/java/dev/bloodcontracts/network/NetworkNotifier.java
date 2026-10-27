package dev.bloodcontracts.network;

import dev.bloodcontracts.config.BCConfig;
import dev.bloodcontracts.core.Bounty;
import dev.bloodcontracts.core.BountyManager;
import dev.bloodcontracts.core.BountyNotifier;
import dev.bloodcontracts.network.BCPayloads.BountyAlertPayload;
import dev.bloodcontracts.network.BCPayloads.RewardsAvailablePayload;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Sends the out-of-chat notifications to clients. Falls back to an action-bar message for clients without the mod. */
public final class NetworkNotifier implements BountyNotifier {
    @Override
    public void bountyPlaced(ServerPlayer target, Bounty bounty, String sponsorName) {
        int onHead = BountyManager.get(target.server).activeOn(target.getUUID()).size();
        Optional<String> sponsor = BCConfig.alertRevealsSponsor() ? Optional.of(sponsorName) : Optional.empty();
        if (!ServerPayloadHandler.send(target, new BountyAlertPayload(sponsor, bounty.displayStacks(), onHead, false))) {
            target.displayClientMessage(Component.translatable("bloodcontracts.alert.title").withStyle(s -> s.withColor(0xFF2020)), true);
        }
    }

    @Override
    public void bountiesOnLogin(ServerPlayer target, List<Bounty> bounties) {
        List<ItemStack> rewards = bounties.stream().flatMap(b -> b.displayStacks().stream()).limit(27).toList();
        if (!ServerPayloadHandler.send(target, new BountyAlertPayload(Optional.empty(), rewards, bounties.size(), true))) {
            target.displayClientMessage(Component.translatable("bloodcontracts.alert.login", bounties.size()), true);
        }
    }

    @Override
    public void targetDown(ServerPlayer sponsor, Bounty bounty, String hunterName, boolean heartGained) {
        String targetName = BountyManager.get(sponsor.server).nameOf(bounty.target());
        if (!ServerPayloadHandler.send(sponsor, new BCPayloads.TargetDownPayload(bounty.target(), targetName, hunterName, heartGained))) {
            sponsor.displayClientMessage(Component.translatable("bloodcontracts.toast.target_down.body", hunterName, targetName), true);
        }
    }

    @Override
    public void loggedIn(ServerPlayer player) {
        ServerPayloadHandler.send(player, BCPayloads.HelpConfigPayload.fromConfig());
    }

    @Override
    public void rewardsAvailable(ServerPlayer player, int count) {
        if (!ServerPayloadHandler.send(player, new RewardsAvailablePayload(count))) {
            player.displayClientMessage(Component.translatable("bloodcontracts.toast.rewards.body", count), true);
        }
    }
}
