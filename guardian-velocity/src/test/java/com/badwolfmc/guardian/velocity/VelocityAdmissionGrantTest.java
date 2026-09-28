package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.protocol.ConnectionOrigin;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VelocityAdmissionGrantTest {
    @Test
    void immutableGrantRetainsOnlyAssertionIdentityAfterMutableSessionIsDiscarded() {
        byte[] id = new byte[GuardianProtocol.PROXY_SESSION_ID_BYTES];
        id[0] = 9;
        VelocityAdmissionGrant grant = new VelocityAdmissionGrant(id, ConnectionOrigin.JAVA);
        id[0] = 0;
        assertEquals(9, grant.proxySessionId()[0]);
        byte[] copy = grant.proxySessionId();
        copy[0] = 1;
        assertEquals(9, grant.proxySessionId()[0]);
        assertEquals(ConnectionOrigin.JAVA, grant.connectionOrigin());
    }

    @Test
    void invalidProxySessionLengthIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new VelocityAdmissionGrant(new byte[1], ConnectionOrigin.JAVA));
    }
}
