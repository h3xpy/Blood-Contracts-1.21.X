package dev.bloodcontracts.core;

import java.util.Objects;
import java.util.UUID;

/**
 * Health moved from a target ({@code from}) to a sponsor ({@code to}) by a collected bounty. {@code remainingHp} goes
 * down as the target regenerates; the transfer is deleted at 0. Balances are always computed from these transfers.
 */
public record HeartTransfer(UUID id, UUID from, UUID to, int remainingHp, long createdAt) {
    public HeartTransfer {
        Objects.requireNonNull(id);
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
        if (remainingHp <= 0) {
            throw new IllegalArgumentException("A transfer carries at least 1 HP");
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("A player cannot transfer health to themself");
        }
    }

    public HeartTransfer minusOneHp() {
        return new HeartTransfer(id, from, to, remainingHp - 1, createdAt);
    }
}
