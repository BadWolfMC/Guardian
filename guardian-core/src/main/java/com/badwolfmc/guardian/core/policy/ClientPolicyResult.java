package com.badwolfmc.guardian.core.policy;

import com.badwolfmc.guardian.core.ClientAction;
import com.badwolfmc.guardian.core.GuardianDecision;

import java.util.Objects;

/** Terminal decision is null only when the shared policy requires Cerberus attestation. */
public record ClientPolicyResult(ClientAction action, GuardianDecision terminalDecision) {
    public ClientPolicyResult {
        Objects.requireNonNull(action, "action");
        if (action == ClientAction.REQUIRE_CERBERUS && terminalDecision != null) {
            throw new IllegalArgumentException("REQUIRE_CERBERUS cannot have a terminal client decision");
        }
        if (action != ClientAction.REQUIRE_CERBERUS && terminalDecision == null) {
            throw new IllegalArgumentException("terminal ALLOW/DENY client actions require a decision");
        }
    }
}
