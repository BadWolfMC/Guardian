package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.BedrockEvidence;
import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.DecisionOutcome;
import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.core.policy.ResolvedAdmissionProfile;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.Manifest;
import com.badwolfmc.guardian.protocol.Presence;

import java.util.Objects;
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
    private final AtomicBoolean summaryLogged = new AtomicBoolean();
    private final byte[] proxySessionId;
    private final VelocityRuntimeSnapshot runtimeSnapshot;
    private volatile byte[] nonce;
    private volatile Presence cerberusPresence;
    private volatile ResolvedAdmissionProfile resolvedProfile;
    private volatile BedrockEvidence bedrockEvidence;
    private volatile String observedBrand;
    private volatile Manifest manifest;

    VelocityAdmissionSession(byte[] proxySessionId, VelocityRuntimeSnapshot runtimeSnapshot) {
        if (proxySessionId == null || proxySessionId.length != GuardianProtocol.PROXY_SESSION_ID_BYTES) {
            throw new IllegalArgumentException(
                "proxySessionId must be " + GuardianProtocol.PROXY_SESSION_ID_BYTES + " bytes");
        }
        this.proxySessionId = proxySessionId.clone();
        this.runtimeSnapshot = runtimeSnapshot;
    }

    /** Test-only convenience for protocol/session invariants that do not need a runtime snapshot. */
    VelocityAdmissionSession(byte[] proxySessionId) {
        this(proxySessionId, null);
    }

    VelocityRuntimeSnapshot runtimeSnapshot() {
        return Objects.requireNonNull(runtimeSnapshot, "runtimeSnapshot");
    }

    CompletableFuture<GuardianDecision> decisionFuture() { return decisionFuture; }
    GuardianDecision decision() { return decision.get(); }

    boolean decide(GuardianDecision value) {
        if (!decision.compareAndSet(null, value)) return false;
        decisionFuture.complete(value);
        return true;
    }

    ClientClassification classification() { return classification.get(); }
    void setClassification(ClientClassification value) { classification.compareAndSet(null, value); }

    Presence cerberusPresence() { return cerberusPresence; }
    synchronized boolean recordPresence(Presence presence) {
        if (cerberusPresence == null) { cerberusPresence = presence; return true; }
        return Objects.equals(cerberusPresence, presence);
    }
    boolean cerberusPresent() { return cerberusPresence != null; }

    void requireCerberus() { cerberusRequired.set(true); }
    boolean cerberusRequired() { return cerberusRequired.get(); }

    GuardianDecision configurationAttestationFailure() { return configurationAttestationFailure.get(); }
    void recordConfigurationAttestationFailure(GuardianDecision failure) {
        configurationAttestationFailure.compareAndSet(null, Objects.requireNonNull(failure, "failure"));
    }

    boolean tryMarkResponseReceived() { return responseReceived.compareAndSet(false, true); }
    boolean challengeSent() { return challengeSent.get(); }
    boolean tryMarkChallengeSent() { return challengeSent.compareAndSet(false, true); }

    byte[] nonce() { return nonce == null ? null : nonce.clone(); }
    void setNonce(byte[] value) { nonce = value.clone(); }

    ResolvedAdmissionProfile resolvedProfile() { return resolvedProfile; }
    synchronized void setResolvedProfile(ResolvedAdmissionProfile value) {
        if (resolvedProfile == null) resolvedProfile = Objects.requireNonNull(value, "value");
    }

    BedrockEvidence bedrockEvidence() { return bedrockEvidence; }
    void setBedrockEvidence(BedrockEvidence value) { bedrockEvidence = value; }
    String observedBrand() { return observedBrand; }
    void setObservedBrand(String value) { observedBrand = value; }
    Manifest manifest() { return manifest; }
    void setManifest(Manifest value) { manifest = value; }
    boolean tryMarkSummaryLogged() { return summaryLogged.compareAndSet(false, true); }

    byte[] proxySessionId() { return proxySessionId.clone(); }

    boolean admitted() {
        GuardianDecision value = decision.get();
        return value != null && value.outcome() == DecisionOutcome.ALLOW;
    }
}
