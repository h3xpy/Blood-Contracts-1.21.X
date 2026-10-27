package dev.bloodcontracts.core;

import net.minecraft.network.chat.Component;

/** Why a {@link BountyManager} operation was refused. When an operation is refused, nothing changed. */
public enum BountyError {
    SELF_TARGET,
    UNKNOWN_TARGET,
    INVALID_SLOT,
    EMPTY_SLOT,
    INVALID_QUANTITY,
    BLACKLISTED,
    LIMIT_REACHED,
    NOT_FOUND,
    NOT_ACTIVE,
    NOT_OWNER,
    NO_MENU,
    NOTHING_TO_CLAIM,
    NOT_ALIVE,
    PAIR_EXISTS,
    INVALID_HUNTERS,
    ALREADY_PUBLIC,
    NOT_CANCELLING;

    public String translationKey() {
        return "bloodcontracts.error." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public Component message() {
        return Component.translatable(translationKey());
    }
}
