package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.protocol.ConnectionOrigin;
import com.badwolfmc.guardian.protocol.GuardianProtocol;

import java.util.Objects;

/** Minimal immutable result retained for backend assertions after mutable Admission state is discarded. */
record VelocityAdmissionGrant(byte[] proxySessionId, ConnectionOrigin connectionOrigin) {
    VelocityAdmissionGrant {
        Objects.requireNonNull(proxySessionId, "proxySessionId");
        Objects.requireNonNull(connectionOrigin, "connectionOrigin");
        if (proxySessionId.length != GuardianProtocol.PROXY_SESSION_ID_BYTES) {
            throw new IllegalArgumentException(
                "proxySessionId must be " + GuardianProtocol.PROXY_SESSION_ID_BYTES + " bytes");
        }
        proxySessionId = proxySessionId.clone();
    }

    @Override
    public byte[] proxySessionId() {
        return proxySessionId.clone();
    }
}
