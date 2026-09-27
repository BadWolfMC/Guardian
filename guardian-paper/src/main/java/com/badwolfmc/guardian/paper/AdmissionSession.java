package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.paper.config.GuardianRuntimeSnapshot;
import com.badwolfmc.guardian.core.policy.ResolvedAdmissionProfile;
import com.badwolfmc.guardian.protocol.ProxyAdmissionAssertion;
import com.badwolfmc.guardian.protocol.Response;
import com.badwolfmc.guardian.protocol.Presence;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class AdmissionSession {
    private final UUID playerId;
    private final GuardianRuntimeSnapshot snapshot;
    private final CompletableFuture<Response> response = new CompletableFuture<>();
    private final AtomicReference<GuardianDecision> decision = new AtomicReference<>();
    private final AtomicBoolean challengeSent = new AtomicBoolean();
    private final AtomicBoolean responseReceived = new AtomicBoolean();
    private final AtomicReference<ProxyAdmissionAssertion> proxyAdmission = new AtomicReference<>();
    private final AtomicReference<GuardianDecision> configurationPresenceFailure = new AtomicReference<>();
    private volatile byte[] nonce;
    private volatile boolean cerberusPresent;
    private volatile Presence cerberusPresence;
    private volatile boolean playHandshakeRequired;
    private volatile boolean quarantined;
    private volatile ClientClassification classification;
    private volatile ResolvedAdmissionProfile resolvedProfile;

    AdmissionSession(UUID playerId, GuardianRuntimeSnapshot snapshot) {
        this.playerId = playerId;
        this.snapshot = snapshot;
    }

    UUID playerId() {
        return playerId;
    }

    GuardianRuntimeSnapshot snapshot() {
        return snapshot;
    }

    CompletableFuture<Response> response() {
        return response;
    }

    GuardianDecision decision() {
        return decision.get();
    }

    void decide(GuardianDecision value) {
        decision.compareAndSet(null, value);
    }

    byte[] nonce() {
        return nonce == null ? null : nonce.clone();
    }

    void setNonce(byte[] nonce) {
        this.nonce = nonce.clone();
    }

    boolean cerberusPresent() {
        return cerberusPresent;
    }

    Presence cerberusPresence() { return cerberusPresence; }

    synchronized boolean recordPresence(Presence presence) {
        if (!cerberusPresent) { cerberusPresence = presence; cerberusPresent = true; return true; }
        return java.util.Objects.equals(cerberusPresence, presence);
    }

    boolean tryMarkResponseReceived() { return responseReceived.compareAndSet(false, true); }

    boolean challengeSent() {
        return challengeSent.get();
    }

    boolean tryMarkChallengeSent() {
        return challengeSent.compareAndSet(false, true);
    }

    void requirePlayHandshake() {
        this.playHandshakeRequired = true;
    }

    boolean playHandshakeRequired() {
        return playHandshakeRequired;
    }

    void setQuarantined(boolean quarantined) {
        this.quarantined = quarantined;
    }

    boolean quarantined() {
        return quarantined;
    }

    ClientClassification classification() {
        return classification;
    }

    void setClassification(ClientClassification classification) {
        this.classification = classification;
    }


    ResolvedAdmissionProfile resolvedProfile() {
        return resolvedProfile;
    }

    synchronized void setResolvedProfile(ResolvedAdmissionProfile value) {
        if (resolvedProfile == null) resolvedProfile = java.util.Objects.requireNonNull(value, "value");
    }

    GuardianDecision configurationPresenceFailure() {
        return configurationPresenceFailure.get();
    }

    void recordConfigurationPresenceFailure(GuardianDecision failure) {
        configurationPresenceFailure.compareAndSet(null, failure);
    }

    ProxyAdmissionAssertion proxyAdmission() {
        return proxyAdmission.get();
    }

    synchronized boolean recordProxyAdmission(ProxyAdmissionAssertion assertion) {
        ProxyAdmissionAssertion existing = proxyAdmission.get();
        if (existing != null) {
            return java.util.Arrays.equals(existing.proxySessionId(), assertion.proxySessionId())
                && existing.playerId().equals(assertion.playerId())
                && existing.connectionOrigin() == assertion.connectionOrigin();
        }
        proxyAdmission.set(assertion);
        return true;
    }
}
