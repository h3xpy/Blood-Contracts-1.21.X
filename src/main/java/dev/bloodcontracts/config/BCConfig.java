package dev.bloodcontracts.config;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server configuration. Every accessor falls back to the default value while the config is not loaded
 * (unit tests, early startup), so callers never have to care.
 */
public final class BCConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue ATTRIBUTION_WINDOW_TICKS;
    public static final ModConfigSpec.IntValue CANNON_OPERATOR_WINDOW_TICKS;
    public static final ModConfigSpec.IntValue CANCEL_LOCK_SECONDS;
    public static final ModConfigSpec.IntValue MAX_ACTIVE_BOUNTIES_PER_SPONSOR;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REWARD_BLACKLIST;
    public static final ModConfigSpec.BooleanValue CREDIT_TAMED_PETS;
    public static final ModConfigSpec.BooleanValue SHOW_SPONSOR;
    public static final ModConfigSpec.BooleanValue ALERT_REVEALS_SPONSOR;
    public static final ModConfigSpec.IntValue HISTORY_SIZE;
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("attribution");
        ATTRIBUTION_WINDOW_TICKS = b
                .comment("How long (ticks) damage from a player keeps them responsible for an environmental death (void, fall, lava...).")
                .defineInRange("attributionWindowTicks", 200, 1, 72000);
        CANNON_OPERATOR_WINDOW_TICKS = b
                .comment("Create Big Cannons: a shot is credited to the last player who directly operated the cannon within this many ticks.")
                .defineInRange("cannonOperatorWindowTicks", 200, 1, 72000);
        CREDIT_TAMED_PETS = b
                .comment("Credit the owner when a tamed pet (wolf...) kills a target.")
                .define("creditTamedPets", true);
        b.pop();

        b.push("bounties");
        CANCEL_LOCK_SECONDS = b
                .comment("A sponsor cannot cancel a bounty while its target took damage from an eligible player within this many seconds.")
                .defineInRange("cancelLockSeconds", 30, 0, 3600);
        MAX_ACTIVE_BOUNTIES_PER_SPONSOR = b
                .comment("Maximum number of active bounties a single player can sponsor.")
                .defineInRange("maxActiveBountiesPerSponsor", 5, 1, 1000);
        REWARD_BLACKLIST = b
                .comment("Item ids that cannot be used as a reward, e.g. [\"minecraft:bedrock\"].")
                .defineListAllowEmpty("rewardBlacklist", List.of(), () -> "minecraft:air",
                        o -> o instanceof String s && ResourceLocation.tryParse(s) != null);
        SHOW_SPONSOR = b
                .comment("Show the sponsor's name in the public bounty list.")
                .define("showSponsor", false);
        ALERT_REVEALS_SPONSOR = b
                .comment("The alert shown to an online target when a bounty is placed on them names the sponsor (independent of showSponsor).")
                .define("alertRevealsSponsor", true);
        HISTORY_SIZE = b
                .comment("How many closed (claimed or cancelled) bounties are kept in the history.")
                .defineInRange("historySize", 200, 0, 100000);
        b.pop();

        DEBUG_LOGGING = b
                .comment("Log every bounty mutation and attribution decision (bounty id and UUIDs).")
                .define("debugLogging", false);
        SPEC = b.build();
    }

    private BCConfig() {}

    private static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static int attributionWindowTicks() { return get(ATTRIBUTION_WINDOW_TICKS); }
    public static int cannonOperatorWindowTicks() { return get(CANNON_OPERATOR_WINDOW_TICKS); }
    public static int cancelLockTicks() { return get(CANCEL_LOCK_SECONDS) * 20; }
    public static int maxActiveBountiesPerSponsor() { return get(MAX_ACTIVE_BOUNTIES_PER_SPONSOR); }
    public static List<? extends String> rewardBlacklist() { return get(REWARD_BLACKLIST); }
    public static boolean creditTamedPets() { return get(CREDIT_TAMED_PETS); }
    public static boolean showSponsor() { return get(SHOW_SPONSOR); }
    public static boolean alertRevealsSponsor() { return get(ALERT_REVEALS_SPONSOR); }
    public static int historySize() { return get(HISTORY_SIZE); }
    public static boolean debug() { return get(DEBUG_LOGGING); }
}
