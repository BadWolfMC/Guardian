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
        assertTrue(minimum.matches("1.8.8-beta"),
            "a prerelease with a greater numeric core remains greater than the minimum");
        assertFalse(minimum.matches("1.8.8-custom"),
            "arbitrary custom suffixes must not receive guessed numeric ordering");
        assertFalse(minimum.matches("1.8.8+"), "empty build metadata is not a comparable numeric candidate");
    }

    @Test
    void minecraftPrefixedFabricPrereleasesUseModVersionRatherThanMinecraftVersion() {
        VersionPredicate minimum = VersionPredicate.parse(">=0.9.2");
        assertTrue(minimum.matches("mc26.3-0.9.3-beta.1-fabric"));
        assertTrue(minimum.matches("mc26.3-0.9.3-alpha.1-fabric"));
        assertTrue(minimum.matches("mc26.3-0.9.3-rc.1-fabric"));
        assertTrue(minimum.matches("mc26.3-0.9.3-fabric"));
        assertTrue(minimum.matches("0.9.3-beta.1"));
        assertFalse(minimum.matches("mc26.3-0.9.1-beta.1-fabric"));
        assertFalse(minimum.matches("mc26.3-0.9.2-beta.1-fabric"));
        assertFalse(minimum.matches("mc26.3-0.9.1-fabric"));
        assertFalse(minimum.matches("mc26.3-build-0.9.3-beta.1-fabric"),
            "opaque and ambiguous versions must not be numerically accepted");

        assertFalse(VersionPredicate.parse(">=0.9.3").matches("mc26.3-0.9.3-beta.1-fabric"));
        assertTrue(VersionPredicate.parse("<0.9.3").matches("mc26.3-0.9.3-beta.1-fabric"));
        assertFalse(VersionPredicate.parse("=0.9.3").matches("mc26.3-0.9.3-beta.1-fabric"));
        assertTrue(VersionPredicate.parse("=0.9.3").matches("mc26.3-0.9.3-fabric"));
        assertFalse(VersionPredicate.parse("0.9.3").matches("mc26.3-0.9.3-fabric"),
            "literal matching must not silently normalize version strings");
        assertTrue(VersionPredicate.parse("mc26.3-0.9.3-*").matches("mc26.3-0.9.3-beta.1-fabric"));
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
