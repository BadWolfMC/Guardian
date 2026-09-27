package com.badwolfmc.guardian.core;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Platform-neutral client-class and JAVA_UNKNOWN brand policy used by each named admission profile.
 *
 * <p>Phase 3 composes this immutable client-policy component with mod/artifact policy in
 * {@code com.badwolfmc.guardian.core.policy.AdmissionProfile}.</p>
 */
public final class AdmissionPolicy {
    private final Map<ClientClassification, ClientAction> clientActions;
    private final UnknownBrandPolicy unknownBrandPolicy;

    public AdmissionPolicy(
        Map<ClientClassification, ClientAction> clientActions,
        UnknownBrandPolicy unknownBrandPolicy
    ) {
        Objects.requireNonNull(clientActions, "clientActions");
        this.unknownBrandPolicy = Objects.requireNonNull(unknownBrandPolicy, "unknownBrandPolicy");

        EnumMap<ClientClassification, ClientAction> copy = new EnumMap<>(ClientClassification.class);
        copy.putAll(clientActions);
        for (ClientClassification classification : ClientClassification.values()) {
            ClientAction action = copy.get(classification);
            if (action == null) {
                throw new IllegalArgumentException("missing client action for " + classification.policyKey());
            }
            if (action == ClientAction.REQUIRE_CERBERUS && classification != ClientClassification.JAVA_FABRIC) {
                throw new IllegalArgumentException(
                    classification.policyKey() + " cannot REQUIRE_CERBERUS; Cerberus is Fabric-only");
            }
        }
        validateUnknownBrandMode(copy.get(ClientClassification.JAVA_UNKNOWN), unknownBrandPolicy.mode());
        this.clientActions = Map.copyOf(copy);
    }

    public static AdmissionPolicy defaults() {
        return new AdmissionPolicy(
            Map.of(
                ClientClassification.BEDROCK, ClientAction.ALLOW,
                ClientClassification.JAVA_VANILLA, ClientAction.ALLOW,
                ClientClassification.JAVA_OPTIFINE, ClientAction.ALLOW,
                ClientClassification.JAVA_FABRIC, ClientAction.REQUIRE_CERBERUS,
                ClientClassification.JAVA_UNKNOWN, ClientAction.DENY
            ),
            new UnknownBrandPolicy(BrandRuleMode.ALLOWLIST, Set.of())
        );
    }

    public ClientAction actionFor(ClientClassification classification, String rawBrand) {
        Objects.requireNonNull(classification, "classification");
        ClientAction base = clientActions.get(classification);
        if (classification != ClientClassification.JAVA_UNKNOWN) {
            return base;
        }
        return unknownBrandPolicy.resolve(rawBrand, base);
    }

    public ClientAction configuredAction(ClientClassification classification) {
        return clientActions.get(Objects.requireNonNull(classification, "classification"));
    }

    public Map<ClientClassification, ClientAction> clientActions() {
        return clientActions;
    }

    public UnknownBrandPolicy unknownBrandPolicy() {
        return unknownBrandPolicy;
    }

    public static void validateUnknownBrandMode(ClientAction unknownAction, BrandRuleMode mode) {
        Objects.requireNonNull(unknownAction, "unknownAction");
        Objects.requireNonNull(mode, "mode");
        if (unknownAction == ClientAction.REQUIRE_CERBERUS) {
            throw new IllegalArgumentException("JAVA_UNKNOWN cannot REQUIRE_CERBERUS; Cerberus is Fabric-only");
        }
        ClientAction requiredFallback = mode == BrandRuleMode.ALLOWLIST ? ClientAction.DENY : ClientAction.ALLOW;
        if (unknownAction != requiredFallback) {
            throw new IllegalArgumentException(
                "unknown client action must be " + requiredFallback + " when unknown brand mode is " + mode);
        }
    }
}
