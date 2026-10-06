package com.badwolfmc.guardian.core.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionPredicateTest {
    @Test
    void exactAndPrefixSupportNonSemverFabricVersions() {
        assertTrue(VersionPredicate.parse("0.9.1+mc26.2").matches("0.9.1+mc26.2"));
        assertFalse(VersionPredicate.parse("0.9.1+mc26.2").matches("0.9.2+mc26.2"));
        assertTrue(VersionPredicate.parse("build-2026.*").matches("build-2026.09-special"));
        assertTrue(VersionPredicate.parse("*").matches("literally-anything"));
    }

    @Test
    void numericRangesAreBoundedAndDeterministic() {
        VersionPredicate range = VersionPredicate.parse(">=1.2 <2.0");
        assertTrue(range.matches("1.2"));
        assertTrue(range.matches("1.9.9"));
        assertFalse(range.matches("2.0"));
        assertTrue(VersionPredicate.parse("=1.2").matches("1.2.0"));
    }

    @Test
    void numericRangesIgnoreBuildMetadataOnDottedNumericVersions() {
        VersionPredicate minimum = VersionPredicate.parse(">=1.8.7");
        assertTrue(minimum.matches("1.8.7+fabric.26.2"));
        assertTrue(minimum.matches("1.8.8+fabric.26.2"));
        assertTrue(minimum.matches("1.9.0+fabric.26.2"));
        assertTrue(minimum.matches("2.0.0+fabric.26.2"));
        assertFalse(minimum.matches("1.8.6+fabric.26.2"));

        assertTrue(VersionPredicate.parse("=1.8.7").matches("1.8.7+fabric.26.2"));
        assertFalse(VersionPredicate.parse("1.8.7").matches("1.8.7+fabric.26.2"),
            "exact text matching must remain distinct from numeric equality");
        assertFalse(minimum.matches("1.8.8-beta"),
            "pre-release/custom suffixes must not receive guessed numeric ordering");
        assertFalse(minimum.matches("1.8.8+"), "empty build metadata is not a comparable numeric candidate");
    }

    @Test
    void malformedPredicatesAreRejectedAtLoadTime() {
        assertThrows(IllegalArgumentException.class, () -> VersionPredicate.parse("1.*.2"));
        assertThrows(IllegalArgumentException.class, () -> VersionPredicate.parse(">=1.2 bananas"));
        assertThrows(IllegalArgumentException.class, () -> VersionPredicate.parse(">=2.0 <1.0"));
        assertThrows(IllegalArgumentException.class, () -> VersionPredicate.parse(">1.0 <=1.0"));
        assertThrows(IllegalArgumentException.class, () -> VersionPredicate.parse("=1.0 =2.0"));
        assertThrows(IllegalArgumentException.class, () -> VersionPredicate.parse("exact version"));
        assertThrows(IllegalArgumentException.class, () -> VersionPredicate.parse(" "));
    }
}
