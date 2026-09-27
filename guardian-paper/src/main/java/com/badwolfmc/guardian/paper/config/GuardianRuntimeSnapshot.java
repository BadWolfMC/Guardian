package com.badwolfmc.guardian.paper.config;

import com.badwolfmc.guardian.core.policy.AdmissionPolicySnapshot;
import com.badwolfmc.guardian.paper.locale.GuardianLocaleCatalog;

import java.util.Objects;

/** One immutable Paper runtime snapshot; admissionPolicy is null when Paper is not policy authority. */
public record GuardianRuntimeSnapshot(
    GuardianPaperSettings settings,
    GuardianLocaleCatalog localeCatalog,
    AdmissionPolicySnapshot admissionPolicy
) {
    public GuardianRuntimeSnapshot {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(localeCatalog, "localeCatalog");
        if (settings.admissionEnabled()
            && settings.authorityMode() == com.badwolfmc.guardian.paper.PaperAuthorityMode.STANDALONE) {
            Objects.requireNonNull(admissionPolicy, "standalone admissionPolicy");
        }
    }

    public AdmissionPolicySnapshot requireAdmissionPolicy() {
        if (admissionPolicy == null) {
            throw new IllegalStateException("Paper is not the active admission-policy authority");
        }
        return admissionPolicy;
    }
}
