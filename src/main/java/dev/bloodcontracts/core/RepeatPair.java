package dev.bloodcontracts.core;

import java.util.UUID;

/** {@code hunter} collected {@code count} bounties on {@code target}. Information only, never acted upon. */
public record RepeatPair(UUID hunter, UUID target, int count) {}
