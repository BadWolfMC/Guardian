package com.badwolfmc.guardian.core;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks one optional Bedrock-origin provider across lifecycle changes without conflating a provider
 * that was never present with one that disappeared after Guardian had relied on it.
 *
 * <p>The generation is part of the security boundary: an observation is trustworthy only when the
 * provider state remained unchanged for its duration. Phase 4's positive-evidence precedence still
 * applies across providers, but a provider that reconfigures during its own observation contributes
 * {@link BedrockSignal#ERROR} rather than stale positive or negative evidence.</p>
 */
public final class BedrockProviderState {
    public enum Availability {
        NEVER_AVAILABLE,
        AVAILABLE,
        LOST
    }

    public record Snapshot(Availability availability, long generation) {
        public Snapshot {
            if (availability == null) throw new NullPointerException("availability");
        }

        public boolean available() {
            return availability == Availability.AVAILABLE;
        }
    }

    private final AtomicReference<Snapshot> state;

    public BedrockProviderState(boolean initiallyAvailable) {
        this.state = new AtomicReference<>(new Snapshot(
            initiallyAvailable ? Availability.AVAILABLE : Availability.NEVER_AVAILABLE, 0L));
    }

    public Snapshot snapshot() {
        return state.get();
    }

    /** Marks the provider currently usable/present. */
    public void markAvailable() {
        update(true);
    }

    /**
     * Marks the provider currently unavailable. A provider that was previously available becomes
     * LOST and therefore security-significant until it is restored.
     */
    public void markUnavailable() {
        update(false);
    }

    /** Signal to use when a query is skipped because the captured snapshot was unavailable. */
    public BedrockSignal unavailableSignal(Snapshot captured) {
        Snapshot current = state.get();
        if (!sameGeneration(captured, current)) {
            return BedrockSignal.ERROR;
        }
        return switch (captured.availability()) {
            case NEVER_AVAILABLE -> BedrockSignal.UNAVAILABLE;
            case LOST -> BedrockSignal.ERROR;
            case AVAILABLE -> throw new IllegalArgumentException("captured provider was available");
        };
    }

    /** Finalizes a clean provider query against the captured lifecycle generation. */
    public BedrockSignal queriedSignal(Snapshot captured, boolean bedrock) {
        return stabilizeSignal(captured, bedrock ? BedrockSignal.BEDROCK : BedrockSignal.NOT_BEDROCK);
    }

    /** Re-checks a completed observation against the current lifecycle generation. */
    public BedrockSignal stabilizeSignal(Snapshot captured, BedrockSignal observed) {
        if (observed == null) throw new NullPointerException("observed");
        return sameGeneration(captured, state.get()) ? observed : BedrockSignal.ERROR;
    }

    private void update(boolean available) {
        while (true) {
            Snapshot current = state.get();
            Availability nextAvailability;
            if (available) {
                nextAvailability = Availability.AVAILABLE;
            } else if (current.availability() == Availability.NEVER_AVAILABLE) {
                nextAvailability = Availability.NEVER_AVAILABLE;
            } else {
                nextAvailability = Availability.LOST;
            }

            if (current.availability() == nextAvailability) {
                return;
            }

            Snapshot next = new Snapshot(nextAvailability, current.generation() + 1L);
            if (state.compareAndSet(current, next)) {
                return;
            }
        }
    }

    private static boolean sameGeneration(Snapshot left, Snapshot right) {
        return left.generation() == right.generation() && left.availability() == right.availability();
    }
}
