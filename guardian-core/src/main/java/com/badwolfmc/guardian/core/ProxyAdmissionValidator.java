package com.badwolfmc.guardian.core;

import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.ProxyAdmissionAssertion;

import java.util.UUID;

/** Validates the non-cryptographic binding/freshness fields of a verified proxy assertion. */
public final class ProxyAdmissionValidator {
    private ProxyAdmissionValidator() {
    }

    public static GuardianDecision validate(
        ProxyAdmissionAssertion assertion, UUID expectedPlayerId, long nowEpochMillis
    ) {
        if (assertion.assertionVersion() != GuardianProtocol.PROXY_ASSERTION_VERSION) {
            return invalid("unsupported proxy assertion version " + assertion.assertionVersion());
        }
        if (!assertion.playerId().equals(expectedPlayerId)) {
            return invalid("proxy assertion UUID does not match authenticated player");
        }

        final long lifetime;
        try {
            lifetime = Math.subtractExact(assertion.expiresAtEpochMillis(), assertion.issuedAtEpochMillis());
        } catch (ArithmeticException ex) {
            return invalid("proxy assertion timestamp range overflowed");
        }
        if (lifetime <= 0L) {
            return invalid("proxy assertion expiry is not after issuance");
        }
        if (lifetime > GuardianProtocol.PROXY_ASSERTION_TTL_MILLIS) {
            return invalid("proxy assertion lifetime exceeds limit");
        }

        final long latestAcceptedIssueTime;
        try {
            latestAcceptedIssueTime = Math.addExact(
                nowEpochMillis, GuardianProtocol.PROXY_ASSERTION_CLOCK_SKEW_MILLIS);
        } catch (ArithmeticException ex) {
            return invalid("proxy assertion clock-skew boundary overflowed");
        }
        if (assertion.issuedAtEpochMillis() > latestAcceptedIssueTime) {
            return invalid("proxy assertion issuance is too far in the future");
        }
        if (assertion.expiresAtEpochMillis() <= nowEpochMillis) {
            return invalid("proxy assertion has expired");
        }
        return GuardianDecision.allow(
            DecisionReason.PROXY_ADMISSION_VERIFIED,
            "trusted Guardian-Velocity admission assertion verified"
        );
    }

    private static GuardianDecision invalid(String detail) {
        return GuardianDecision.deny(DecisionReason.PROXY_ASSERTION_INVALID, detail);
    }
}
