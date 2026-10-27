package dev.bloodcontracts.debug;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.bloodcontracts.client.BountyBoardScreen;
import dev.bloodcontracts.client.ClientPayloadHandler;
import dev.bloodcontracts.core.BountyState;
import dev.bloodcontracts.core.BountyType;
import dev.bloodcontracts.core.PendingDelivery;
import dev.bloodcontracts.network.BCPayloads;
import dev.bloodcontracts.network.BCPayloads.BountyListPayload;
import dev.bloodcontracts.network.BCPayloads.DeliveryView;
import dev.bloodcontracts.network.BCPayloads.KnownPlayersPayload;
import dev.bloodcontracts.network.BountyView;
import dev.bloodcontracts.network.TargetView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * Development-only scripted client run ({@code ./gradlew runBoardDebug}): fills the bounty board with sample data and
 * screenshots every tab, the detail view, the hunter picker and the creation screen (run/screenshots/board_*.png).
 */
@Mod(value = "bloodcontracts_debug", dist = Dist.CLIENT)
public class BoardScenario {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final TreeMap<Integer, Runnable> steps = new TreeMap<>();
    private int tick = -1;
    private int inWorldFor;
    private BountyBoardScreen board;
    private UUID detailTarget;

    public BoardScenario() {
        if (!"board".equals(System.getProperty("bloodcontracts.debugScenario"))) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        step(0, () -> {
            board = new BountyBoardScreen();
            mc.setScreen(board);
            ClientPayloadHandler.handleKnownPlayers(new KnownPlayersPayload(knownPlayers(), List.of(
                    new TargetView.RepeatView(detailTarget(), "Fennec", 3))));
            board.update(samplePayload(mc));
        });
        shot(10, "01_wanted");
        step(12, () -> call("openDetail", UUID.class, detailTarget));
        shot(18, "02_detail");
        step(20, () -> call("openPicker"));
        shot(26, "03_picker");
        step(28, () -> {
            set("picking", false);
            set("detail", null);
            tab("MINE");
        });
        shot(32, "04_mine");
        step(34, () -> tab("CLAIM"));
        shot(38, "05_claim");
        step(40, () -> tab("HISTORY"));
        shot(44, "06_history");
        step(46, () -> tab("HELP"));
        shot(50, "07_help");
        step(52, () -> PacketDistributor.sendToServer(BCPayloads.OpenCreateMenuRequest.INSTANCE));
        shot(60, "08_create_public");
        step(62, () -> {
            try {
                Field type = mc.screen.getClass().getDeclaredField("type");
                type.setAccessible(true);
                type.set(mc.screen, BountyType.PRIVATE);
            } catch (ReflectiveOperationException e) {
                LOGGER.error("[board-debug] cannot switch type", e);
            }
        });
        shot(68, "09_create_private");
        step(72, mc::stop);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> onTick(mc));
    }

    private UUID detailTarget() {
        if (detailTarget == null) {
            detailTarget = UUID.nameUUIDFromBytes("Brigand".getBytes());
        }
        return detailTarget;
    }

    private static final String[] NAMES = {"Brigand", "Fennec", "Corvus", "Mirabelle", "Tobias", "Ysolde", "Marie", "Lucas", "Inès", "Hugo", "Sacha"};

    private static UUID id(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes());
    }

    private static List<KnownPlayersPayload.KnownPlayer> knownPlayers() {
        List<KnownPlayersPayload.KnownPlayer> out = new ArrayList<>();
        for (int i = 0; i < NAMES.length; i++) {
            out.add(new KnownPlayersPayload.KnownPlayer(id(NAMES[i]), NAMES[i], i % 3 == 0));
        }
        return out;
    }

    private BountyListPayload samplePayload(Minecraft mc) {
        long now = System.currentTimeMillis();
        UUID me = mc.player.getUUID();
        List<TargetView> wanted = new ArrayList<>();
        // Detailed target: three bounties, one of them mine (private, under notice), immune for a while.
        UUID brigand = detailTarget();
        List<BountyView> onBrigand = List.of(
                view(brigand, "Brigand", "Ysolde", false, BountyType.PUBLIC, List.of(new ItemStack(Items.DIAMOND, 12)), now - 7_200_000, Optional.empty(), Optional.empty(), true),
                view(brigand, "Brigand", "Joueur1", true, BountyType.PRIVATE, List.of(new ItemStack(Items.NETHERITE_INGOT, 2), new ItemStack(Items.GOLDEN_APPLE, 5)),
                        now - 3_600_000, Optional.of(List.of(new BountyView.HunterRef(id("Fennec"), "Fennec"), new BountyView.HunterRef(id("Marie"), "Marie"))),
                        Optional.of(now + 23 * 60_000), false),
                view(brigand, "Brigand", "Lucas", false, BountyType.PUBLIC, List.of(new ItemStack(Items.EMERALD, 30)), now - 600_000, Optional.empty(), Optional.empty(), true));
        wanted.add(new TargetView(new GameProfile(brigand, "Brigand"), Optional.of(now + 72 * 60_000), onBrigand,
                List.of(new TargetView.RepeatView(brigand, "Fennec", 3))));
        ItemStack[] rewards = {new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.ELYTRA), new ItemStack(Items.IRON_INGOT, 64),
                new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2), new ItemStack(Items.BEACON),
                new ItemStack(Items.SHULKER_BOX), new ItemStack(Items.TRIDENT), new ItemStack(Items.GOLD_BLOCK, 9), new ItemStack(Items.CAKE)};
        for (int i = 2; i < NAMES.length; i++) {
            UUID t = id(NAMES[i]);
            boolean priv = i % 4 == 0;
            boolean notice = i == 5;
            wanted.add(new TargetView(new GameProfile(t, NAMES[i]), Optional.empty(), List.of(
                    view(t, NAMES[i], "Hugo", false, priv ? BountyType.PRIVATE : BountyType.PUBLIC, List.of(rewards[i - 2]),
                            now - i * 300_000L, Optional.empty(), notice ? Optional.of(now + 9 * 60_000) : Optional.empty(), !priv)),
                    List.of()));
        }
        // Mine first in the "my bounties" list: put my own one on another target too.
        List<BountyView> history = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            UUID t = id(NAMES[i]);
            BountyState state = i % 3 == 2 ? BountyState.CANCELLED : BountyState.CLAIMED;
            history.add(new BountyView(UUID.randomUUID(), t, NAMES[i], Optional.empty(), false, List.of(rewards[i]), now - 86_400_000L,
                    state, state == BountyState.CLAIMED ? Optional.of(NAMES[(i + 3) % NAMES.length]) : Optional.empty(), now - i * 3_600_000L,
                    i == 1 ? BountyType.PRIVATE : BountyType.PUBLIC, Optional.empty(), Optional.empty(), false));
        }
        List<DeliveryView> claimable = List.of(
                new DeliveryView(UUID.randomUUID(), new ItemStack(Items.DIAMOND, 12), PendingDelivery.Reason.REWARD, "Ysolde"),
                new DeliveryView(UUID.randomUUID(), new ItemStack(Items.EMERALD, 4), PendingDelivery.Reason.REFUND, "Corvus"));
        return new BountyListPayload(wanted, history, claimable, now, false, Optional.empty(), false);
    }

    private static BountyView view(UUID target, String name, String sponsor, boolean mine, BountyType type, List<ItemStack> rewards, long created,
                                   Optional<List<BountyView.HunterRef>> hunters, Optional<Long> cancelAt, boolean eligible) {
        return new BountyView(UUID.randomUUID(), target, name, mine ? Optional.of(sponsor) : Optional.empty(), mine, rewards, created,
                BountyState.ACTIVE, Optional.empty(), 0L, type, hunters, cancelAt, eligible);
    }

    private void tab(String name) {
        try {
            Class<?> tabClass = Class.forName("dev.bloodcontracts.client.BountyBoardScreen$Tab");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object value = Enum.valueOf((Class<Enum>) tabClass, name);
            Method m = BountyBoardScreen.class.getDeclaredMethod("switchTab", tabClass);
            m.setAccessible(true);
            m.invoke(board, value);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("[board-debug] cannot switch tab", e);
        }
    }

    private void call(String method, Class<?> type, Object arg) {
        try {
            Method m = BountyBoardScreen.class.getDeclaredMethod(method, type);
            m.setAccessible(true);
            m.invoke(board, arg);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("[board-debug] {} failed", method, e);
        }
    }

    private void call(String method) {
        try {
            Method m = BountyBoardScreen.class.getDeclaredMethod(method);
            m.setAccessible(true);
            m.invoke(board);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("[board-debug] {} failed", method, e);
        }
    }

    private void set(String field, Object value) {
        try {
            Field f = BountyBoardScreen.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(board, value);
            Method refresh = BountyBoardScreen.class.getDeclaredMethod("refreshWidgets");
            refresh.setAccessible(true);
            refresh.invoke(board);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("[board-debug] cannot set {}", field, e);
        }
    }

    private void step(int at, Runnable r) {
        steps.merge(at, r, (a, b) -> () -> {
            a.run();
            b.run();
        });
    }

    private void shot(int at, String name) {
        step(at, () -> Screenshot.grab(Minecraft.getInstance().gameDirectory, "board_" + name + ".png",
                Minecraft.getInstance().getMainRenderTarget(), msg -> LOGGER.info("[board-debug] {}", msg.getString())));
    }

    private void onTick(Minecraft mc) {
        if (tick < 0) {
            if (mc.player == null || mc.level == null || mc.screen != null || ++inWorldFor < 60) {
                return;
            }
            tick = 0;
            LOGGER.info("[board-debug] scenario started");
        }
        Runnable r = steps.get(tick);
        if (r != null) {
            r.run();
        }
        tick++;
    }
}
