package dev.bloodcontracts.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.bloodcontracts.config.BCConfig;
import dev.bloodcontracts.core.Bounty;
import dev.bloodcontracts.core.BountyManager;
import dev.bloodcontracts.core.BountyResult;
import dev.bloodcontracts.core.BountySavedData;
import dev.bloodcontracts.core.BountyState;
import dev.bloodcontracts.core.BountyType;
import dev.bloodcontracts.core.HeartTransfer;
import dev.bloodcontracts.core.RepeatPair;
import dev.bloodcontracts.network.ServerPayloadHandler;
import dev.bloodcontracts.util.Durations;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /bounty ...}. Every mutation goes through {@link BountyManager}. Bounties can only be widened: there is no
 * command to remove a hunter or to make a public bounty private.
 */
public final class BountyCommands {
    private static final SimpleCommandExceptionType UNKNOWN_ID =
            new SimpleCommandExceptionType(Component.translatable("bloodcontracts.error.not_found"));
    private static final DynamicCommandExceptionType UNKNOWN_PLAYER =
            new DynamicCommandExceptionType(name -> Component.translatable("bloodcontracts.command.unknown_player", name));

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

        // Any player who ever joined, online or not: bounties on offline players are allowed.
        SuggestionProvider<CommandSourceStack> knownNames = (ctx, builder) -> SharedSuggestionProvider.suggest(
                manager(ctx).data().knownPlayers().values(), builder);
        // Several names separated by spaces: suggest for the last one.
        SuggestionProvider<CommandSourceStack> knownNameList = (ctx, builder) -> {
            String typed = builder.getRemaining();
            int cut = typed.lastIndexOf(' ') + 1;
            return SharedSuggestionProvider.suggest(manager(ctx).data().knownPlayers().values(), builder.createOffset(builder.getStart() + cut));
        };

        dispatcher.register(Commands.literal("bounty")
                .then(Commands.literal("place")
                        .then(Commands.argument("target", StringArgumentType.word()).suggests(knownNames)
                                .executes(ctx -> place(ctx, BountyType.PUBLIC, null))
                                .then(Commands.literal("public").executes(ctx -> place(ctx, BountyType.PUBLIC, null)))
                                .then(Commands.literal("private")
                                        .then(Commands.argument("hunters", StringArgumentType.greedyString()).suggests(knownNameList)
                                                .executes(ctx -> place(ctx, BountyType.PRIVATE, StringArgumentType.getString(ctx, "hunters")))))))
                .then(Commands.literal("addhunter")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(ownActiveIds)
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(knownNames)
                                        .executes(BountyCommands::addHunter))))
                .then(Commands.literal("makepublic")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(ownActiveIds)
                                .executes(ctx -> sponsorAction(ctx, BountyManager::makePublic, "bloodcontracts.command.made_public"))))
                .then(Commands.literal("list").executes(ctx -> list(ctx, false)))
                .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(anyIds)
                                .executes(BountyCommands::info)))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(ownActiveIds)
                                .executes(BountyCommands::requestCancel)))
                .then(Commands.literal("resume")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(ownActiveIds)
                                .executes(ctx -> sponsorAction(ctx, BountyManager::resume, "bloodcontracts.command.resumed"))))
                .then(Commands.literal("history").executes(BountyCommands::history))
                .then(Commands.literal("claim").executes(BountyCommands::claim))
                .then(Commands.literal("gui").executes(ctx -> {
                    ServerPayloadHandler.sendList(ctx.getSource().getPlayerOrException(), true, null, false);
                    return 1;
                }))
                .then(Commands.literal("admin")
                        .requires(src -> src.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("id", StringArgumentType.word()).suggests(activeIds)
                                        .executes(BountyCommands::adminCancel)))
                        .then(Commands.literal("list").executes(ctx -> list(ctx, true)))
                        .then(Commands.literal("clearimmunity")
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(knownNames)
                                        .executes(BountyCommands::clearImmunity)))
                        .then(Commands.literal("hearts")
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(knownNames)
                                        .executes(BountyCommands::hearts)))));
    }

    private static BountyManager manager(CommandContext<CommandSourceStack> ctx) {
        return BountyManager.get(ctx.getSource().getServer());
    }

    private static UUID player(BountyManager manager, String name) throws CommandSyntaxException {
        return manager.findKnownPlayer(name).orElseThrow(() -> UNKNOWN_PLAYER.create(name));
    }

    /** Fails the command with the manager's refusal, or returns the value. */
    private static <T> Optional<T> check(CommandContext<CommandSourceStack> ctx, BountyResult<T> result) {
        if (!result.isOk()) {
            ctx.getSource().sendFailure(result.error().message());
            return Optional.empty();
        }
        return Optional.of(result.value());
    }

    private static int place(CommandContext<CommandSourceStack> ctx, BountyType type, String hunterNames) throws CommandSyntaxException {
        ServerPlayer sponsor = ctx.getSource().getPlayerOrException();
        BountyManager manager = manager(ctx);
        UUID target = player(manager, StringArgumentType.getString(ctx, "target"));
        Set<UUID> hunters = new LinkedHashSet<>();
        if (hunterNames != null) {
            for (String name : hunterNames.trim().split("[\\s,]+")) {
                if (!name.isEmpty()) {
                    hunters.add(player(manager, name));
                }
            }
        }
        Inventory inv = sponsor.getInventory();
        Optional<Bounty> placed = check(ctx, manager.create(sponsor, target, type, hunters, inv, List.of(inv.selected)));
        placed.ifPresent(b -> ctx.getSource().sendSuccess(() -> Component.translatable(
                b.type() == BountyType.PRIVATE ? "bloodcontracts.command.placed_private" : "bloodcontracts.command.placed",
                reward(b), Component.literal(manager.nameOf(target)).withStyle(ChatFormatting.RED), id(b), b.hunters().size()), false));
        if (placed.isPresent() && type == BountyType.PUBLIC) {
            ctx.getSource().sendSuccess(() -> Component.translatable("bloodcontracts.gui.public_warning").withStyle(ChatFormatting.GRAY), false);
        }
        return placed.isPresent() ? 1 : 0;
    }

    private static int addHunter(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BountyManager manager = manager(ctx);
        Bounty b = resolve(manager, StringArgumentType.getString(ctx, "id"), false);
        UUID hunter = player(manager, StringArgumentType.getString(ctx, "player"));
        UUID actor = ctx.getSource().getPlayerOrException().getUUID();
        Optional<Bounty> done = check(ctx, manager.addHunters(actor, b.id(), List.of(hunter)));
        done.ifPresent(w -> ctx.getSource().sendSuccess(() -> Component.translatable("bloodcontracts.command.hunter_added",
                manager.nameOf(hunter), id(w), w.hunters().size()), false));
        return done.isPresent() ? 1 : 0;
    }

    /** A sponsor-only change on one of their bounties (make public, resume). */
    private static int sponsorAction(CommandContext<CommandSourceStack> ctx, Action action, String successKey) throws CommandSyntaxException {
        BountyManager manager = manager(ctx);
        Bounty b = resolve(manager, StringArgumentType.getString(ctx, "id"), false);
        UUID actor = ctx.getSource().getPlayerOrException().getUUID();
        Optional<Bounty> done = check(ctx, action.apply(manager, actor, b.id()));
        done.ifPresent(d -> ctx.getSource().sendSuccess(() -> Component.translatable(successKey, id(d)), false));
        return done.isPresent() ? 1 : 0;
    }

    @FunctionalInterface
    private interface Action {
        BountyResult<Bounty> apply(BountyManager manager, UUID actor, UUID bountyId);
    }

    private static int requestCancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BountyManager manager = manager(ctx);
        Bounty b = resolve(manager, StringArgumentType.getString(ctx, "id"), false);
        UUID actor = ctx.getSource().getPlayerOrException().getUUID();
        Optional<Bounty> done = check(ctx, manager.requestCancel(actor, b.id()));
        done.ifPresent(d -> ctx.getSource().sendSuccess(() -> d.isActive() && d.cancelAt() != null
                ? Component.translatable("bloodcontracts.command.cancel_requested", id(d), Durations.of(d.cancelAt() - manager.now()))
                : Component.translatable("bloodcontracts.command.cancelled", id(d)), false));
        return done.isPresent() ? 1 : 0;
    }

    private static int adminCancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BountyManager manager = manager(ctx);
        Bounty b = resolve(manager, StringArgumentType.getString(ctx, "id"), false);
        Optional<Bounty> done = check(ctx, manager.adminCancel(b.id()));
        done.ifPresent(d -> ctx.getSource().sendSuccess(() -> Component.translatable("bloodcontracts.command.cancelled", id(d)), true));
        return done.isPresent() ? 1 : 0;
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
        ServerPlayer viewer = ctx.getSource().getPlayer();
        boolean showSponsor = admin || BCConfig.showSponsor();
        for (Bounty b : active) {
            MutableComponent line = Component.empty().append(id(b)).append(" ")
                    .append(Component.literal(manager.nameOf(b.target())).withStyle(ChatFormatting.RED))
                    .append(" — ").append(reward(b))
                    .append(Component.literal(" (" + date(b.createdAt()) + ")").withStyle(ChatFormatting.GRAY));
            status(manager, b, line);
            if (viewer != null) {
                line.append(" ").append(Component.translatable(manager.canCollect(viewer.getUUID(), b)
                        ? "bloodcontracts.gui.can_collect" : "bloodcontracts.gui.cannot_collect").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (showSponsor) {
                line.append(Component.translatable("bloodcontracts.command.by", manager.nameOf(b.sponsor())).withStyle(ChatFormatting.GRAY));
            }
            if (admin && b.type() == BountyType.PRIVATE) {
                line.append(Component.translatable("bloodcontracts.command.hunters", names(manager, b.hunters())).withStyle(ChatFormatting.GRAY));
            }
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return active.size();
    }

    /** Type, pending cancellation and immunity, appended to a bounty line. */
    private static void status(BountyManager manager, Bounty b, MutableComponent line) {
        if (b.type() == BountyType.PRIVATE) {
            line.append(" ").append(Component.translatable("bloodcontracts.command.private_tag").withStyle(ChatFormatting.DARK_PURPLE));
        }
        if (b.isActive() && b.cancelAt() != null) {
            line.append(" ").append(Component.translatable("bloodcontracts.gui.expires_in", Durations.of(b.cancelAt() - manager.now()))
                    .withStyle(ChatFormatting.RED));
        }
        if (b.isActive()) {
            manager.immuneUntil(b.target()).ifPresent(until -> line.append(" ")
                    .append(Component.translatable("bloodcontracts.gui.immune_for", Durations.of(until - manager.now())).withStyle(ChatFormatting.AQUA)));
        }
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BountyManager manager = manager(ctx);
        Bounty b = resolve(manager, StringArgumentType.getString(ctx, "id"), true);
        ServerPlayer viewer = ctx.getSource().getPlayer();
        boolean op = ctx.getSource().hasPermission(Commands.LEVEL_GAMEMASTERS);
        boolean mine = viewer != null && viewer.getUUID().equals(b.sponsor());
        boolean showSponsor = BCConfig.showSponsor() || op || mine;
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.translatable("bloodcontracts.command.info_header", id(b)).withStyle(ChatFormatting.DARK_RED), false);
        src.sendSuccess(() -> Component.translatable("bloodcontracts.command.info_target", manager.nameOf(b.target())), false);
        src.sendSuccess(() -> Component.translatable("bloodcontracts.command.info_reward", reward(b)), false);
        src.sendSuccess(() -> Component.translatable("bloodcontracts.command.info_type",
                Component.translatable("bloodcontracts.type." + b.type().name().toLowerCase(java.util.Locale.ROOT))), false);
        // The private hunter list is confidential: sponsor and operators only.
        if (b.type() == BountyType.PRIVATE && (mine || op)) {
            src.sendSuccess(() -> Component.translatable("bloodcontracts.command.info_hunters", names(manager, b.hunters())), false);
        }
        src.sendSuccess(() -> Component.translatable("bloodcontracts.command.info_state",
                Component.translatable("bloodcontracts.state." + b.state().name().toLowerCase(java.util.Locale.ROOT)), date(b.createdAt())), false);
        MutableComponent extra = Component.empty();
        status(manager, b, extra);
        if (!extra.getSiblings().isEmpty()) {
            src.sendSuccess(() -> extra, false);
        }
        if (showSponsor) {
            src.sendSuccess(() -> Component.translatable("bloodcontracts.command.info_sponsor", manager.nameOf(b.sponsor())), false);
        }
        if (b.state() == BountyState.CLAIMED && b.claimedBy() != null) {
            src.sendSuccess(() -> Component.translatable("bloodcontracts.command.info_claimed", manager.nameOf(b.claimedBy()), date(b.closedAt())), false);
        }
        for (RepeatPair pair : manager.repeatPairs(b.target())) {
            src.sendSuccess(() -> Component.translatable("bloodcontracts.gui.repeat_pair", manager.nameOf(pair.hunter()), pair.count(),
                    manager.nameOf(pair.target())).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    /** Operators: a player's heart balance, the transfers behind it, the regeneration gauge and immunity. */
    private static int hearts(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BountyManager manager = manager(ctx);
        UUID who = player(manager, StringArgumentType.getString(ctx, "player"));
        BountySavedData data = manager.data();
        CommandSourceStack src = ctx.getSource();
        int hp = manager.heartBalanceHp(who);
        src.sendSuccess(() -> Component.translatable("bloodcontracts.command.hearts_header", manager.nameOf(who), hearts(hp))
                .withStyle(ChatFormatting.DARK_RED), false);
        List<HeartTransfer> involved = new ArrayList<>(data.transfers().stream()
                .filter(t -> t.from().equals(who) || t.to().equals(who)).toList());
        for (HeartTransfer t : involved) {
            src.sendSuccess(() -> Component.translatable("bloodcontracts.command.hearts_transfer", manager.nameOf(t.from()),
                    manager.nameOf(t.to()), hearts(t.remainingHp()), date(t.createdAt())), false);
        }
        data.regen(who).ifPresent(g -> src.sendSuccess(() -> Component.translatable("bloodcontracts.command.hearts_gauge",
                String.format(java.util.Locale.ROOT, "%.2f", g.progress()),
                Component.translatable(BCConfig.heartRegenMode() == BCConfig.HeartRegenMode.PROGRESSIVE
                        ? "bloodcontracts.command.regen_progressive" : "bloodcontracts.command.regen_permanent")), false));
        manager.immuneUntil(who).ifPresent(until -> src.sendSuccess(() ->
                Component.translatable("bloodcontracts.gui.immune_for", Durations.of(until - manager.now())), false));
        return hp;
    }

    /** Operators: ends a player's immunity now, so bounties on them can be collected again. */
    private static int clearImmunity(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BountyManager manager = manager(ctx);
        UUID who = player(manager, StringArgumentType.getString(ctx, "player"));
        if (!manager.clearImmunity(who)) {
            ctx.getSource().sendFailure(Component.translatable("bloodcontracts.command.not_immune", manager.nameOf(who)));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("bloodcontracts.command.immunity_cleared", manager.nameOf(who)), true);
        return 1;
    }

    /** "-1.5" hearts from HP. */
    private static String hearts(int hp) {
        String s = (hp % 2 == 0) ? Integer.toString(hp / 2) : String.format(java.util.Locale.ROOT, "%.1f", hp / 2.0);
        return hp > 0 ? "+" + s : s;
    }

    private static String names(BountyManager manager, List<UUID> players) {
        return String.join(", ", players.stream().map(manager::nameOf).toList());
    }

    private static int claim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<Integer> claimed = check(ctx, manager(ctx).claimRewards(player, Optional.empty()));
        claimed.ifPresent(n -> ctx.getSource().sendSuccess(() -> Component.translatable("bloodcontracts.gui.claimed", n), false));
        return claimed.orElse(0);
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
            if (b.type() == BountyType.PRIVATE) {
                line.append(" ").append(Component.translatable("bloodcontracts.command.private_tag").withStyle(ChatFormatting.DARK_PURPLE));
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
        MutableComponent out = Component.empty().withStyle(ChatFormatting.GOLD);
        List<ItemStack> stacks = b.displayStacks();
        for (int i = 0; i < stacks.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(Component.literal(stacks.get(i).getCount() + "x ")).append(stacks.get(i).getHoverName());
        }
        return out;
    }

    private static String date(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(millis));
    }
}
