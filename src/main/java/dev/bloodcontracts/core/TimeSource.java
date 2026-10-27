package dev.bloodcontracts.core;

/**
 * Real-time clock for everything measured in wall-clock time (cancel notice, immunity, heart regeneration).
 * Injectable so tests can move time forward.
 */
@FunctionalInterface
public interface TimeSource {
    TimeSource SYSTEM = System::currentTimeMillis;

    long nowMillis();
}
