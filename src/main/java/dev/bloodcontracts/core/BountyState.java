package dev.bloodcontracts.core;

public enum BountyState {
    /** The reward is held in escrow and can be claimed or cancelled. */
    ACTIVE,
    /** A hunter killed the target; the reward was handed to a delivery. */
    CLAIMED,
    /** The sponsor (or an operator) cancelled it; the reward was handed back through a delivery. */
    CANCELLED
}
