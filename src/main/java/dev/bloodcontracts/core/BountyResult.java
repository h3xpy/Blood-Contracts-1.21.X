package dev.bloodcontracts.core;

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/** Outcome of a {@link BountyManager} operation: either a value or a {@link BountyError}. */
public record BountyResult<T>(@Nullable T value, @Nullable BountyError error) {
    public static <T> BountyResult<T> ok(T value) {
        return new BountyResult<>(Objects.requireNonNull(value), null);
    }

    public static <T> BountyResult<T> fail(BountyError error) {
        return new BountyResult<>(null, Objects.requireNonNull(error));
    }

    public boolean isOk() {
        return error == null;
    }
}
