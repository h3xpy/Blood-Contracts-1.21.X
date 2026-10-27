package dev.bloodcontracts.config;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server configuration. Every accessor falls back to the default value while the config is not loaded
 * (unit tests, early startup), so callers never have to care.
 */
public final class BCConfig {
    public enum HeartRegenMode { PROGRESSIVE, PERMANENT }

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue ATTRIBUTION_WINDOW_TICKS;
    public static final ModConfigSpec.IntValue CANNON_OPERATOR_WINDOW_TICKS;
    /** Obsolete since 2.0 (replaced by the cancel notice). Kept so existing config files stay valid. */
    public static final ModConfigSpec.IntValue CANCEL_LOCK_SECONDS;
    public static final ModConfigSpec.IntValue CANCEL_NOTICE_MINUTES;
    public static final ModConfigSpec.IntValue MAX_ACTIVE_BOUNTIES_PER_SPONSOR;
    public static final ModConfigSpec.IntValue MAX_HUNTER_LIST_SIZE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REWARD_BLACKLIST;
    public static final ModConfigSpec.BooleanValue CREDIT_TAMED_PETS;
    public static final ModConfigSpec.BooleanValue SHOW_SPONSOR;
    public static final ModConfigSpec.BooleanValue ALERT_REVEALS_SPONSOR;
    public static final ModConfigSpec.IntValue HISTORY_SIZE;
    public static final ModConfigSpec.IntValue REPEAT_PAIR_THRESHOLD;
    public static final ModConfigSpec.IntValue MAX_HEARTS_LOST_PER_DEATH;
    public static final ModConfigSpec.IntValue MAX_HEARTS_LOST;
    public static final ModConfigSpec.IntValue MAX_HEARTS_GAINED;
    public static final ModConfigSpec.EnumValue<HeartRegenMode> HEART_REGEN_MODE;
    public static final ModConfigSpec.IntValue HEART_REGEN_ONLINE_STEP_MINUTES;
    public static final ModConfigSpec.IntValue HEART_REGEN_OFFLINE_STEP_MINUTES;
    public static final ModConfigSpec.IntValue TARGET_IMMUNITY_MINUTES;
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
                .comment("OBSOLETE since 2.0, has no effect: replaced by cancelNoticeMinutes.")
                .defineInRange("cancelLockSeconds", 30, 0, 3600);
        CANCEL_NOTICE_MINUTES = b
                .comment("A cancelled bounty stays active and collectable for this many real-time minutes before the item goes back.")
                .defineInRange("cancelNoticeMinutes", 30, 0, 10080);
        MAX_ACTIVE_BOUNTIES_PER_SPONSOR = b
                .comment("Maximum number of active bounties a single player can sponsor (bounties under cancel notice count).")
                .defineInRange("maxActiveBountiesPerSponsor", 3, 1, 1000);
        MAX_HUNTER_LIST_SIZE = b
                .comment("Maximum number of hunters on a private bounty.")
                .defineInRange("maxHunterListSize", 20, 1, 1000);
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
        REPEAT_PAIR_THRESHOLD = b
                .comment("A hunter who collected more than this many bounties on the same target is shown on the target's poster (information only).")
                .defineInRange("repeatPairThreshold", 2, 0, 1000);
        b.pop();

        b.push("hearts");
        MAX_HEARTS_LOST_PER_DEATH = b
                .comment("Hearts a target loses (to a sponsor) on a death that collects at least one bounty, however many are collected.")
                .defineInRange("maxHeartsLostPerDeath", 1, 0, 10);
        MAX_HEARTS_LOST = b
                .comment("A player's net balance never goes below -maxHeartsLost hearts.")
                .defineInRange("maxHeartsLost", 5, 0, 9);
        MAX_HEARTS_GAINED = b
                .comment("A player's net balance never goes above +maxHeartsGained hearts.")
                .defineInRange("maxHeartsGained", 5, 0, 100);
        HEART_REGEN_MODE = b
                .comment("PROGRESSIVE: lost hearts come back half a heart at a time. PERMANENT: they never come back.")
                .defineEnum("heartRegenMode", HeartRegenMode.PROGRESSIVE);
        HEART_REGEN_ONLINE_STEP_MINUTES = b
                .comment("PROGRESSIVE: 1 HP (half a heart) comes back every this many minutes the target spends online.")
                .defineInRange("heartRegenOnlineStepMinutes", 90, 1, 100000);
        HEART_REGEN_OFFLINE_STEP_MINUTES = b
                .comment("PROGRESSIVE: 1 HP (half a heart) comes back every this many real-time minutes the target spends offline.")
                .defineInRange("heartRegenOfflineStepMinutes", 720, 1, 1000000);
        TARGET_IMMUNITY_MINUTES = b
                .comment("After a death that collected a bounty, no bounty can be collected on that target for this many real-time minutes.")
                .defineInRange("targetImmunityMinutes", 120, 0, 100000);
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

    private static final long MINUTE = 60_000L;

    public static int attributionWindowTicks() { return get(ATTRIBUTION_WINDOW_TICKS); }
    public static int cannonOperatorWindowTicks() { return get(CANNON_OPERATOR_WINDOW_TICKS); }
    public static long cancelNoticeMillis() { return get(CANCEL_NOTICE_MINUTES) * MINUTE; }
    public static int maxActiveBountiesPerSponsor() { return get(MAX_ACTIVE_BOUNTIES_PER_SPONSOR); }
    public static int maxHunterListSize() { return get(MAX_HUNTER_LIST_SIZE); }
    public static List<? extends String> rewardBlacklist() { return get(REWARD_BLACKLIST); }
    public static boolean creditTamedPets() { return get(CREDIT_TAMED_PETS); }
    public static boolean showSponsor() { return get(SHOW_SPONSOR); }
    public static boolean alertRevealsSponsor() { return get(ALERT_REVEALS_SPONSOR); }
    public static int historySize() { return get(HISTORY_SIZE); }
    public static int repeatPairThreshold() { return get(REPEAT_PAIR_THRESHOLD); }
    /** In HP (1 heart = 2 HP). */
    public static int maxHpLostPerDeath() { return get(MAX_HEARTS_LOST_PER_DEATH) * 2; }
    public static int maxHpLost() { return get(MAX_HEARTS_LOST) * 2; }
    public static int maxHpGained() { return get(MAX_HEARTS_GAINED) * 2; }
    public static HeartRegenMode heartRegenMode() { return get(HEART_REGEN_MODE); }
    public static long heartRegenOnlineStepMillis() { return get(HEART_REGEN_ONLINE_STEP_MINUTES) * MINUTE; }
    public static long heartRegenOfflineStepMillis() { return get(HEART_REGEN_OFFLINE_STEP_MINUTES) * MINUTE; }
    public static long targetImmunityMillis() { return get(TARGET_IMMUNITY_MINUTES) * MINUTE; }
    public static boolean debug() { return get(DEBUG_LOGGING); }
}
