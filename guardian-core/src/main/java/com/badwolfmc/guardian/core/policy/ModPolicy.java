package com.badwolfmc.guardian.core.policy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable profile-scoped Fabric manifest policy. */
public final class ModPolicy {
    private final ModPolicyMode mode;
    private final OriginPolicyAction directoryAction;
    private final OriginPolicyAction mixedOrUnknownAction;
    private final Set<String> baselineModIds;
    private final Map<String, RequiredModRule> requiredByModId;
    private final Map<String, ModRule> rulesByModId;

    public ModPolicy(
        ModPolicyMode mode,
        OriginPolicyAction directoryAction,
        OriginPolicyAction mixedOrUnknownAction,
        Set<String> baselineModIds,
        Map<String, RequiredModRule> requiredByModId,
        Map<String, ModRule> rulesByModId
    ) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.directoryAction = Objects.requireNonNull(directoryAction, "directoryAction");
        this.mixedOrUnknownAction = Objects.requireNonNull(mixedOrUnknownAction, "mixedOrUnknownAction");
        this.baselineModIds = Collections.unmodifiableSet(new LinkedHashSet<>(baselineModIds));
        this.requiredByModId = Collections.unmodifiableMap(new LinkedHashMap<>(requiredByModId));
        this.rulesByModId = Collections.unmodifiableMap(new LinkedHashMap<>(rulesByModId));
    }

    public ModPolicyMode mode() { return mode; }
    public OriginPolicyAction directoryAction() { return directoryAction; }
    public OriginPolicyAction mixedOrUnknownAction() { return mixedOrUnknownAction; }
    public Set<String> baselineModIds() { return baselineModIds; }
    public Map<String, RequiredModRule> requiredByModId() { return requiredByModId; }
    public Map<String, ModRule> rulesByModId() { return rulesByModId; }
}
