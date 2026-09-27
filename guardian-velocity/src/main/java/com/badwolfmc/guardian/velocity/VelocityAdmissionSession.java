package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.DecisionOutcome;
import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.core.policy.ResolvedAdmissionProfile;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.Presence;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class VelocityAdmissionSession {
    private final CompletableFuture<GuardianDecision> decisionFuture = new CompletableFuture<>();
    private final AtomicReference<GuardianDecision> decision = new AtomicReference<>();
    private final AtomicReference<ClientClassification> classification = new AtomicReference<>();
    private final AtomicReference<GuardianDecision> configurationAttestationFailure = new AtomicReference<>();
    private final AtomicBoolean challengeSent = new AtomicBoolean();
    private final AtomicBoolean cerberusRequired = new AtomicBoolean();
    private final AtomicBoolean responseReceived = new AtomicBoolean();
    private final byte[] proxySessionId;
    private volatile byte[] nonce;
    private volatile Presence cerberusPresence;
    private volatile ResolvedAdmissionProfile resolvedProfile;

    VelocityAdmissionSession(byte[] proxySessionId) {
        if (proxySessionId == null || proxySessionId.length != GuardianProtocol.PROXY_SESSION_ID_BYTES) {
            throw new IllegalArgumentException(
                "proxySessionId must be " + GuardianProtocol.PROXY_SESSION_ID_BYTES + " bytes");
        }
        this.proxySessionId = proxySessionId.clone();
    }

    CompletableFuture<GuardianDecision> decisionFuture() {
        return decisionFuture;
    }

    GuardianDecision decision() {
        return decision.get();
    }

    boolean decide(GuardianDecision value) {
        if (!decision.compareAndSet(null, value)) {
            return false;
        }
        decisionFuture.complete(value);
        return true;
    }

    ClientClassification classification() {
        return classification.get();
    }

    void setClassification(ClientClassification value) {
        classification.compareAndSet(null, value);
    }

    Presence cerberusPresence() { return cerberusPresence; }

    synchronized boolean recordPresence(Presence presence) {
        if (cerberusPresence == null) { cerberusPresence = presence; return true; }
        return java.util.Objects.equals(cerberusPresence, presence);
    }

    boolean cerberusPresent() { return cerberusPresence != null; }

    void requireCerberus() { cerberusRequired.set(true); }

    boolean cerberusRequired() { return cerberusRequired.get(); }

    GuardianDecision configurationAttestationFailure() { return configurationAttestationFailure.get(); }

    void recordConfigurationAttestationFailure(GuardianDecision failure) {
        configurationAttestationFailure.compareAndSet(null, java.util.Objects.requireNonNull(failure, "failure"));
    }

    boolean tryMarkResponseReceived() { return responseReceived.compareAndSet(false, true); }

    boolean challengeSent() {
        return challengeSent.get();
    }

    boolean tryMarkChallengeSent() {
        return challengeSent.compareAndSet(false, true);
    }

    byte[] nonce() {
        return nonce == null ? null : nonce.clone();
    }

    void setNonce(byte[] value) {
        nonce = value.clone();
    }

    ResolvedAdmissionProfile resolvedProfile() { return resolvedProfile; }

    synchronized void setResolvedProfile(ResolvedAdmissionProfile value) {
        if (resolvedProfile == null) resolvedProfile = java.util.Objects.requireNonNull(value, "value");
    }

    byte[] proxySessionId() {
        return proxySessionId.clone();
    }

    boolean admitted() {
        GuardianDecision value = decision.get();
        return value != null && value.outcome() == DecisionOutcome.ALLOW;
    }
}
