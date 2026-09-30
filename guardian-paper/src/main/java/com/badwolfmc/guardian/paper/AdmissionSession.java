package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.core.BedrockEvidence;
import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.paper.config.GuardianRuntimeSnapshot;
import com.badwolfmc.guardian.core.policy.ResolvedAdmissionProfile;
import com.badwolfmc.guardian.protocol.Manifest;
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
    private final Object configurationConnectionIdentity;
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
    private volatile BedrockEvidence bedrockEvidence;
    private volatile String observedBrand;
    private volatile Manifest manifest;
    private volatile BackendInspectionSnapshot.FloodgateSanity backendFloodgateSanity;
    private final AtomicBoolean summaryLogged = new AtomicBoolean();
    private final AtomicReference<Object> playConnectionIdentity = new AtomicReference<>();
    private final AtomicBoolean readyForPlay = new AtomicBoolean();

    AdmissionSession(UUID playerId, GuardianRuntimeSnapshot snapshot) {
        this(playerId, snapshot, null);
    }

    AdmissionSession(UUID playerId, GuardianRuntimeSnapshot snapshot, Object configurationConnectionIdentity) {
        this.playerId = playerId;
        this.snapshot = snapshot;
        this.configurationConnectionIdentity = configurationConnectionIdentity;
    }

    UUID playerId() {
        return playerId;
    }

    GuardianRuntimeSnapshot snapshot() {
        return snapshot;
    }

    boolean matchesConfigurationConnection(Object connectionIdentity) {
        return configurationConnectionIdentity != null && configurationConnectionIdentity == connectionIdentity;
    }

    Object configurationConnectionIdentity() {
        return configurationConnectionIdentity;
    }

    boolean bindPlayConnection(Object connectionIdentity) {
        java.util.Objects.requireNonNull(connectionIdentity, "connectionIdentity");
        Object existing = playConnectionIdentity.get();
        return existing == connectionIdentity
            || (existing == null && playConnectionIdentity.compareAndSet(null, connectionIdentity));
    }

    boolean matchesPlayConnection(Object connectionIdentity) {
        return playConnectionIdentity.get() == connectionIdentity;
    }

    boolean playConnectionBound() {
        return playConnectionIdentity.get() != null;
    }

    Object playConnectionIdentity() {
        return playConnectionIdentity.get();
    }

    void markReadyForPlay() {
        readyForPlay.set(true);
    }

    boolean readyForPlay() {
        return readyForPlay.get();
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


    BedrockEvidence bedrockEvidence() { return bedrockEvidence; }

    void setBedrockEvidence(BedrockEvidence value) { bedrockEvidence = value; }

    String observedBrand() { return observedBrand; }

    void setObservedBrand(String value) { observedBrand = value; }

    Manifest manifest() { return manifest; }

    void setManifest(Manifest value) { manifest = value; }

    BackendInspectionSnapshot.FloodgateSanity backendFloodgateSanity() { return backendFloodgateSanity; }

    void setBackendFloodgateSanity(BackendInspectionSnapshot.FloodgateSanity value) {
        backendFloodgateSanity = value;
    }

    boolean tryMarkSummaryLogged() { return summaryLogged.compareAndSet(false, true); }

    ProxyAdmissionAssertion proxyAdmission() {
        return proxyAdmission.get();
    }

    boolean recordProxyAdmission(ProxyAdmissionAssertion assertion) {
        return proxyAdmission.compareAndSet(null, java.util.Objects.requireNonNull(assertion, "assertion"));
    }
}
