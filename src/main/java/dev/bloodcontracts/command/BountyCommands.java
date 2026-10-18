package dev.bloodcontracts.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.bloodcontracts.config.BCConfig;
import dev.bloodcontracts.core.Bounty;
import dev.bloodcontracts.core.BountyManager;
import dev.bloodcontracts.core.BountyResult;
import dev.bloodcontracts.core.BountyState;
import dev.bloodcontracts.network.ServerPayloadHandler;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;

/**
 * {@code /bounty ...}. Every mutation goes through {@link BountyManager}.
 */
public final class BountyCommands {
    private static final SimpleCommandExceptionType UNKNOWN_ID =
            new SimpleCommandExceptionType(Component.translatable("bloodcontracts.error.not_found"));
    private static final SimpleCommandExceptionType ONE_TARGET =
            new SimpleCommandExceptionType(Component.translatable("bloodcontracts.command.one_target"));

    private BountyCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        SuggestionProvider<CommandSourceStack> activeIds = (ctx, builder) -> SharedSuggestionProvider.suggest(
                manager(ctx).activeBounties().stream().map(Bounty::shortId), builder);
        SuggestionProvider<CommandSourceStack> ownActiveIds = (ctx, builder) -> {
            UUID self = ctx.getSource().getPlayer() != null ? ctx.getSource().getPlayer().getUUID() : null;
            return SharedSuggestionProvider.suggest(manager(ctx).activeBounties().stream()
                    .filter(b -> b.sponsor().equals(self)).map(Bounty::shortId), builder);
        };
        SuggestionProvider<CommandSourceStack> anyIds = (ctx, builder) -> SharedSuggestionProvider.suggest(
                Stream.concat(manager(ctx).activeBounties().stream(), manager(ctx).history().stream()).map(Bounty::shortId), builder);

        dispatcher.register(Commands.literal("bounty")
                .then(Commands.literal("place")
                        .then(Commands.argument("target", GameProfileArgument.gameProfile())
                                .executes(BountyCommands::place)))
                .then(Commands.literal("list").executes(ctx -> list(ctx, false)))
                .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(anyIds)
                                .executes(BountyCommands::info)))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(ownActiveIds)
                                .executes(ctx -> cancel(ctx, false))))
                .then(Commands.literal("history").executes(BountyCommands::history))
                .then(Commands.literal("gui").executes(ctx -> {
                    ServerPayloadHandler.sendList(ctx.getSource().getPlayerOrException(), true, null, false);
                    return 1;
                }))
                .then(Commands.literal("admin")
                        .requires(src -> src.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("id", StringArgumentType.word()).suggests(activeIds)
                                        .executes(ctx -> cancel(ctx, true))))
                        .then(Commands.literal("list").executes(ctx -> list(ctx, true)))));
    }

    private static BountyManager manager(CommandContext<CommandSourceStack> ctx) {
        return BountyManager.get(ctx.getSource().getServer());
    }

    private static int place(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sponsor = ctx.getSource().getPlayerOrException();
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "target");
        if (profiles.size() != 1) {
            throw ONE_TARGET.create();
        }
        GameProfile target = profiles.iterator().next();
        Inventory inv = sponsor.getInventory();
        int slot = inv.selected; // main hand
        BountyResult<Bounty> result = manager(ctx).create(sponsor, target.getId(), inv, slot);
        if (!result.isOk()) {
            ctx.getSource().sendFailure(result.error().message());
            return 0;
        }
        Bounty b = result.value();
        ctx.getSource().sendSuccess(() -> Component.translatable("bloodcontracts.command.placed",
                reward(b), Component.literal(target.getName()).withStyle(ChatFormatting.RED), id(b)), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, boolean admin) {
        BountyManager manager = manager(ctx);
        List<Bounty> active = manager.activeBounties();
        if (active.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("bloodcontracts.command.none_active"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("bloodcontracts.command.active_header", active.size())
                .withStyle(ChatFormatting.DARK_RED), false);
        boolean showSponsor = admin || BCConfig.showSponsor();
        for (Bounty b : active) {
            MutableComponent line = Component.empty().append(id(b)).append(" ")
                    .append(Component.literal(manager.nameOf(b.target())).withStyle(ChatFormatting.RED))
                    .append(" — ").append(reward(b))
                    .append(Component.literal(" (" + date(b.createdAt()) + ")").withStyle(ChatFormatting.GRAY));
            if (showSponsor) {
                line.append(Component.translatable("bloodcontracts.command.by", manager.nameOf(b.sponsor())).withStyle(ChatFormatting.GRAY));
            }
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return active.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BountyManager manager = manager(ctx);
        Bounty b = resolve(manager, StringArgumentType.getString(ctx, "id"), true);
        ServerPlayer viewer = ctx.getSource().getPlayer();
        boolean showSponsor = BCConfig.showSponsor() || ctx.getSource().hasPermission(Commands.LEVEL_GAMEMASTERS)
                || (viewer != null && viewer.getUUID().equals(b.sponsor()));
        ctx.getSource().sendSuccess(() -> Component.translatable("bloodcontracts.command.info_header", id(b))
                .withStyle(ChatFormatting.DARK_RED), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("bloodcontracts.command.info_target", manager.nameOf(b.target())), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("bloodcontracts.command.info_reward", reward(b)), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("bloodcontracts.command.info_state",
                Component.translatable("bloodcontracts.state." + b.state().name().toLowerCase(java.util.Locale.ROOT)), date(b.createdAt())), false);
        if (showSponsor) {
            ctx.getSource().sendSuccess(() -> Component.translatable("bloodcontracts.command.info_sponsor", manager.nameOf(b.sponsor())), false);
        }
        if (b.state() == BountyState.CLAIMED && b.claimedBy() != null) {
            ctx.getSource().sendSuccess(() -> Component.translatable("bloodcontracts.command.info_claimed",
                    manager.nameOf(b.claimedBy()), date(b.closedAt())), false);
        }
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> ctx, boolean admin) throws CommandSyntaxException {
        BountyManager manager = manager(ctx);
        Bounty b = resolve(manager, StringArgumentType.getString(ctx, "id"), true);
        UUID actor;
        if (admin) {
            ServerPlayer p = ctx.getSource().getPlayer();
            actor = p != null ? p.getUUID() : new UUID(0L, 0L);
        } else {
            actor = ctx.getSource().getPlayerOrException().getUUID();
        }
        BountyResult<Bounty> result = manager.cancel(actor, b.id(), admin);
        if (!result.isOk()) {
            ctx.getSource().sendFailure(result.error().message());
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("bloodcontracts.command.cancelled", id(b)), admin);
        return 1;
    }

    private static int history(CommandContext<CommandSourceStack> ctx) {
        BountyManager manager = manager(ctx);
        List<Bounty> history = manager.history();
        if (history.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("bloodcontracts.command.no_history"), false);
            return 0;
        }
        history.stream().limit(20).forEach(b -> {
            MutableComponent line = Component.empty().append(id(b)).append(" ")
                    .append(Component.literal(manager.nameOf(b.target())).withStyle(ChatFormatting.RED))
                    .append(" — ").append(reward(b)).append(" — ");
            if (b.state() == BountyState.CLAIMED && b.claimedBy() != null) {
                line.append(Component.translatable("bloodcontracts.command.claimed_by", manager.nameOf(b.claimedBy())).withStyle(ChatFormatting.GOLD));
            } else {
                line.append(Component.translatable("bloodcontracts.state.cancelled").withStyle(ChatFormatting.GRAY));
            }
            line.append(Component.literal(" (" + date(b.closedAt()) + ")").withStyle(ChatFormatting.GRAY));
            ctx.getSource().sendSuccess(() -> line, false);
        });
        return history.size();
    }

    /** Accepts a full UUID or a unique prefix (the 8-character short id). */
    static Bounty resolve(BountyManager manager, String raw, boolean includeHistory) throws CommandSyntaxException {
        String needle = raw.toLowerCase(java.util.Locale.ROOT);
        try {
            Optional<Bounty> exact = manager.findAny(UUID.fromString(needle));
            if (exact.isPresent()) {
                return exact.get();
            }
        } catch (IllegalArgumentException ignored) {
            // not a full UUID, try as a prefix
        }
        Stream<Bounty> candidates = manager.activeBounties().stream();
        if (includeHistory) {
            candidates = Stream.concat(candidates, manager.history().stream());
        }
        List<Bounty> matches = candidates.filter(b -> b.id().toString().startsWith(needle)).toList();
        if (matches.size() != 1) {
            throw UNKNOWN_ID.create();
        }
        return matches.get(0);
    }

    private static Component id(Bounty b) {
        return Component.literal("[" + b.shortId() + "]").withStyle(ChatFormatting.DARK_GRAY);
    }

    private static Component reward(Bounty b) {
        if (!b.reward().isEmpty()) {
            return Component.literal(b.reward().getCount() + "x ").append(b.reward().getDisplayName()).withStyle(ChatFormatting.GOLD);
        }
        var item = BuiltInRegistries.ITEM.get(b.rewardItem());
        return Component.literal(b.rewardCount() + "x ").append(Component.translatable(item.getDescriptionId())).withStyle(ChatFormatting.GOLD);
    }

    private static String date(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(millis));
    }
}
