package com.badwolfmc.guardian.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockProviderStateTest {
    @Test
    void neverPresentProviderIsOrdinaryUnavailable() {
        BedrockProviderState state = new BedrockProviderState(false);
        BedrockProviderState.Snapshot snapshot = state.snapshot();
        assertFalse(snapshot.available());
        assertEquals(BedrockProviderState.Availability.NEVER_AVAILABLE, snapshot.availability());
        assertEquals(BedrockSignal.UNAVAILABLE, state.unavailableSignal(snapshot));
    }

    @Test
    void providerLossAfterAvailabilityBecomesErrorUntilRestored() {
        BedrockProviderState state = new BedrockProviderState(true);
        state.markUnavailable();
        BedrockProviderState.Snapshot lost = state.snapshot();
        assertEquals(BedrockProviderState.Availability.LOST, lost.availability());
        assertEquals(BedrockSignal.ERROR, state.unavailableSignal(lost));

        state.markAvailable();
        assertTrue(state.snapshot().available());
    }

    @Test
    void lateProviderEnableBecomesUsable() {
        BedrockProviderState state = new BedrockProviderState(false);
        state.markAvailable();
        BedrockProviderState.Snapshot available = state.snapshot();
        assertTrue(available.available());
        assertEquals(BedrockSignal.NOT_BEDROCK, state.queriedSignal(available, false));
    }

    @Test
    void negativeQueryFailsClosedAcrossConcurrentProviderChange() {
        BedrockProviderState state = new BedrockProviderState(true);
        BedrockProviderState.Snapshot before = state.snapshot();
        state.markUnavailable();
        assertEquals(BedrockSignal.ERROR, state.queriedSignal(before, false));
    }

    @Test
    void skippedUnavailableObservationFailsClosedIfProviderChangesDuringObservation() {
        BedrockProviderState state = new BedrockProviderState(false);
        BedrockProviderState.Snapshot before = state.snapshot();
        state.markAvailable();
        assertEquals(BedrockSignal.ERROR, state.unavailableSignal(before));
    }

    @Test
    void providerChangeInvalidatesEvenPositiveObservationFromThatProvider() {
        BedrockProviderState state = new BedrockProviderState(true);
        BedrockProviderState.Snapshot before = state.snapshot();
        state.markUnavailable();
        assertEquals(BedrockSignal.ERROR, state.queriedSignal(before, true));
    }

    @Test
    void repeatedStableAvailabilityUpdatesDoNotInvalidateQueryGeneration() {
        BedrockProviderState state = new BedrockProviderState(true);
        BedrockProviderState.Snapshot before = state.snapshot();
        state.markAvailable();
        assertEquals(BedrockSignal.NOT_BEDROCK, state.queriedSignal(before, false));
    }
}
