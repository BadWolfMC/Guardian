package com.badwolfmc.guardian.protocol;

import java.util.*;
import java.util.regex.Pattern;

public final class ManifestCanonicalizer {
    private static final Comparator<ManifestEntry> ORDER = Comparator.comparing(ManifestEntry::modId);
    private static final Pattern FABRIC_MOD_ID = Pattern.compile("[a-z][a-z0-9_-]{1,63}");
    private ManifestCanonicalizer() {}

    public static Manifest canonicalize(Manifest manifest) {
        List<ManifestEntry> sorted = manifest.entries().stream().sorted(ORDER).toList();
        Manifest result = new Manifest(manifest.minecraftVersion(), manifest.fabricLoaderVersion(), manifest.cerberusVersion(), manifest.capabilities(), sorted);
        validate(result, false);
        return result;
    }

    public static void validateCanonical(Manifest manifest) { validate(manifest, true); }

    private static void validate(Manifest manifest, boolean requireOrder) {
        bounded(manifest.minecraftVersion(), GuardianProtocol.MAX_RELEASE_METADATA_BYTES, "minecraft version");
        bounded(manifest.fabricLoaderVersion(), GuardianProtocol.MAX_RELEASE_METADATA_BYTES, "Fabric Loader version");
        bounded(manifest.cerberusVersion(), GuardianProtocol.MAX_RELEASE_METADATA_BYTES, "Cerberus version");
        if (manifest.entries().size() > GuardianProtocol.MAX_MANIFEST_ENTRIES) throw new IllegalArgumentException("manifest entry count exceeds limit");
        Map<String,ManifestEntry> byId=new HashMap<>(); String previous=null;
        for(ManifestEntry e:manifest.entries()){
            bounded(e.modId(),GuardianProtocol.MAX_MOD_ID_BYTES,"mod id"); if (!FABRIC_MOD_ID.matcher(e.modId()).matches()) throw new IllegalArgumentException("invalid Fabric mod id: "+e.modId()); bounded(e.version(),GuardianProtocol.MAX_VERSION_BYTES,"mod version");
            if(e.parentModId()!=null) bounded(e.parentModId(),GuardianProtocol.MAX_MOD_ID_BYTES,"parent mod id");
            boolean nestedOrigin = e.originKind() == OriginKind.NESTED;
            boolean hasParent = e.parentModId() != null;
            if (nestedOrigin != hasParent) {
                throw new IllegalArgumentException(
                    "manifest containment/origin mismatch for " + e.modId()
                        + ": NESTED origin and parentModId must either both be present or both be absent");
            }
            boolean requiresArtifactHash = e.originKind() == OriginKind.ARCHIVE;
            if (requiresArtifactHash && e.artifactSha256() == null) {
                throw new IllegalArgumentException("top-level archive is missing SHA-256 artifact identity: " + e.modId());
            }
            if (!requiresArtifactHash && e.artifactSha256() != null) {
                throw new IllegalArgumentException("artifact SHA-256 is only valid for top-level archive entries: " + e.modId());
            }
            if(byId.put(e.modId(),e)!=null) throw new IllegalArgumentException("duplicate mod id: "+e.modId());
            if(e.modId().equals(e.parentModId())) throw new IllegalArgumentException("mod cannot contain itself: "+e.modId());
            if(requireOrder && previous!=null && previous.compareTo(e.modId())>=0) throw new IllegalArgumentException("manifest entries are not in canonical order"); previous=e.modId();
        }
        for(ManifestEntry e:manifest.entries()) if(e.parentModId()!=null&&!byId.containsKey(e.parentModId())) throw new IllegalArgumentException("missing containing mod: "+e.parentModId());
        for(ManifestEntry e:manifest.entries()) { Set<String> seen=new HashSet<>(); ManifestEntry cursor=e; int depth=0; while(cursor.parentModId()!=null){if(!seen.add(cursor.modId()))throw new IllegalArgumentException("containment cycle at "+cursor.modId());if(++depth>GuardianProtocol.MAX_RELATIONSHIP_DEPTH)throw new IllegalArgumentException("containment depth exceeds limit");cursor=byId.get(cursor.parentModId());} }
    }
    private static void bounded(String value,int max,String name){ProtocolText.validate(value,max,name);}
}
