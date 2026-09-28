package com.badwolfmc.guardian.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockEvidenceTest {
    @Test
    void positiveSupportedEvidenceAlwaysResolvesBedrock() {
        assertEquals(BedrockResolution.BEDROCK,
            new BedrockEvidence(BedrockSignal.BEDROCK, BedrockSignal.NOT_BEDROCK).resolution());
        assertEquals(BedrockResolution.BEDROCK,
            new BedrockEvidence(BedrockSignal.UNAVAILABLE, BedrockSignal.BEDROCK).resolution());
        assertEquals(BedrockResolution.BEDROCK,
            new BedrockEvidence(BedrockSignal.ERROR, BedrockSignal.BEDROCK).resolution());
    }

    @Test
    void absentOrNegativeProvidersCanConcludeJava() {
        assertEquals(BedrockResolution.JAVA,
            new BedrockEvidence(BedrockSignal.UNAVAILABLE, BedrockSignal.UNAVAILABLE).resolution());
        assertEquals(BedrockResolution.JAVA,
            new BedrockEvidence(BedrockSignal.NOT_BEDROCK, BedrockSignal.UNAVAILABLE).resolution());
        assertEquals(BedrockResolution.JAVA,
            new BedrockEvidence(BedrockSignal.NOT_BEDROCK, BedrockSignal.NOT_BEDROCK).resolution());
    }

    @Test
    void providerFailureWithoutPositiveEvidenceIsIndeterminate() {
        assertEquals(BedrockResolution.INDETERMINATE,
            new BedrockEvidence(BedrockSignal.ERROR, BedrockSignal.UNAVAILABLE).resolution());
        assertEquals(BedrockResolution.INDETERMINATE,
            new BedrockEvidence(BedrockSignal.NOT_BEDROCK, BedrockSignal.ERROR).resolution());
    }

    @Test
    void disagreementRequiresTwoExplicitContradictoryAnswers() {
        assertTrue(new BedrockEvidence(BedrockSignal.BEDROCK, BedrockSignal.NOT_BEDROCK).disagrees());
        assertTrue(new BedrockEvidence(BedrockSignal.NOT_BEDROCK, BedrockSignal.BEDROCK).disagrees());
        assertFalse(new BedrockEvidence(BedrockSignal.BEDROCK, BedrockSignal.UNAVAILABLE).disagrees());
        assertFalse(new BedrockEvidence(BedrockSignal.BEDROCK, BedrockSignal.ERROR).disagrees());
        assertFalse(new BedrockEvidence(BedrockSignal.BEDROCK, BedrockSignal.BEDROCK).disagrees());
    }
}
