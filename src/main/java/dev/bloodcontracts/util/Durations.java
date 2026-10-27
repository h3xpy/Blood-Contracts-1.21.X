package dev.bloodcontracts.util;

import net.minecraft.network.chat.Component;

/** Human durations ("1 h 12", "23 min"), shared by commands and screens. */
public final class Durations {
    private Durations() {}

    /** Rounded up to the minute, so a running countdown never shows "0 min". */
    public static Component of(long millis) {
        long minutes = Math.max(0L, (millis + 59_999L) / 60_000L);
        if (minutes >= 60) {
            return Component.translatable("bloodcontracts.time.hours_minutes", minutes / 60, String.format("%02d", minutes % 60));
        }
        return Component.translatable("bloodcontracts.time.minutes", minutes);
    }

    /** Whole minutes as a duration ("2 h 00", "30 min"). */
    public static Component ofMinutes(long minutes) {
        return of(minutes * 60_000L);
    }
}
