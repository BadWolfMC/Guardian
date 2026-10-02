package com.badwolfmc.guardian.core.policy;

import com.badwolfmc.guardian.core.AdmissionPermissions;
import com.badwolfmc.guardian.core.AdmissionPolicy;
import com.badwolfmc.guardian.core.BrandClassifier;
import com.badwolfmc.guardian.core.BrandRuleMode;
import com.badwolfmc.guardian.core.ClientAction;
import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.UnknownBrandPolicy;
import com.badwolfmc.guardian.core.artifact.ApprovedArtifact;
import com.badwolfmc.guardian.core.artifact.ArtifactCatalog;
import com.badwolfmc.guardian.core.artifact.ArtifactCatalogException;
import com.badwolfmc.guardian.core.artifact.ArtifactCatalogStore;
import com.badwolfmc.guardian.core.operations.SafeRegularFile;
import com.badwolfmc.guardian.protocol.ArtifactSha256;
import com.badwolfmc.guardian.protocol.CerberusReleaseCrypto;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.schema.CoreSchema;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/** Strict, platform-neutral parser/normalizer/validator for shared Guardian admission policy YAML. */
public final class AdmissionPolicyLoader {
    public static final int SCHEMA_VERSION = 2;
    public static final int MAX_POLICY_BYTES = 1024 * 1024;
    private static final int MAX_PROFILES = 64;
    private static final int MAX_RULES_PER_PROFILE = 512;
    private static final int MAX_ACCEPTANCES_PER_RULE = 128;
    private static final int MAX_BRAND_RULES = 256;
    private static final int MAX_CERBERUS_RELEASE_KEYS = 8;
    private static final Pattern PROFILE_ID = Pattern.compile("[a-z0-9._-]{1,48}");
    private static final Pattern RULE_ID = Pattern.compile("[a-z0-9._-]{1,64}");
    private static final Pattern MOD_ID = Pattern.compile("[a-z][a-z0-9_-]{1,63}");
    private static final Set<String> ROOT_KEYS = Set.of(
        "schema-version", "default-profile", "identity-overrides", "profiles", "cerberus-release-trust");
    private static final Set<String> PROFILE_KEYS = Set.of("priority", "clients", "unknown-brands", "mods");
    private static final Set<String> CLIENT_KEYS = Set.of("bedrock", "vanilla", "optifine", "fabric", "unknown");
    private static final Set<String> UNKNOWN_BRAND_KEYS = Set.of("mode", "brands");
    private static final Set<String> MODS_KEYS = Set.of("mode", "origins", "baseline", "required", "rules");
    private static final Set<String> ORIGIN_KEYS = Set.of("directory", "mixed-or-unknown");
    private static final Set<String> REQUIRED_KEYS = Set.of("mod", "accept");
    private static final Set<String> RULE_KEYS = Set.of("mod", "action", "accept");
    private static final Set<String> ACCEPT_KEYS = Set.of("version", "verification", "catalog", "sha256");
    private static final Set<String> CERBERUS_RELEASE_TRUST_KEYS = Set.of("required", "ed25519-public-keys");

    public AdmissionPolicySnapshot load(Path policyPath, Path artifactCatalogPath) throws AdmissionPolicyException {
        Objects.requireNonNull(policyPath, "policyPath");
        Objects.requireNonNull(artifactCatalogPath, "artifactCatalogPath");
        Map<String, Object> root = loadYaml(policyPath);
        rejectUnknown(root, ROOT_KEYS, policyPath, "root");

        int schema = integer(root, "schema-version", policyPath, "root");
        if (schema != SCHEMA_VERSION) {
            throw error(policyPath, "unsupported schema-version " + schema
                + "; supported value is " + SCHEMA_VERSION);
        }
        String defaultProfile = normalizedProfileId(string(root, "default-profile", policyPath, "root"), policyPath);

        ArtifactCatalog catalog;
        try {
            catalog = new ArtifactCatalogStore(artifactCatalogPath).load();
        } catch (ArtifactCatalogException ex) {
            throw new AdmissionPolicyException(artifactCatalogPath,
                "artifact catalog is invalid: " + ex.getMessage(), ex);
        }

        Map<UUID, String> overrides = parseOverrides(root.get("identity-overrides"), policyPath);
        CerberusReleaseTrust releaseTrust = parseCerberusReleaseTrust(
            root.get("cerberus-release-trust"), policyPath);
        Map<String, Object> rawProfiles = map(root.get("profiles"), policyPath, "profiles");
        if (rawProfiles.isEmpty()) throw error(policyPath, "profiles must contain at least one profile");
        if (rawProfiles.size() > MAX_PROFILES) {
            throw error(policyPath, "profiles exceeds " + MAX_PROFILES + " profiles");
        }

        LinkedHashMap<String, AdmissionProfile> profiles = new LinkedHashMap<>();
        HashSet<Integer> priorities = new HashSet<>();
        for (Map.Entry<String, Object> raw : rawProfiles.entrySet()) {
            String profileId = normalizedProfileId(raw.getKey(), policyPath);
            if (profiles.containsKey(profileId)) {
                throw error(policyPath, "duplicate normalized profile id '" + profileId + "'");
            }
            AdmissionProfile profile = parseProfile(profileId, raw.getValue(), catalog, policyPath);
            if (!priorities.add(profile.priority())) {
                throw error(policyPath, "profile priority " + profile.priority()
                    + " is used more than once; profile priorities must be unique for deterministic resolution");
            }
            profiles.put(profileId, profile);
            // Validate the stable administrator-facing permission form now, not at connection time.
            try {
                AdmissionPermissions.profile(profileId);
            } catch (IllegalArgumentException ex) {
                throw error(policyPath, "invalid profile permission for '" + profileId + "': " + ex.getMessage());
            }
        }
        if (!profiles.containsKey(defaultProfile)) {
            throw error(policyPath, "default-profile '" + defaultProfile + "' does not exist");
        }
        for (Map.Entry<UUID, String> override : overrides.entrySet()) {
            if (!profiles.containsKey(override.getValue())) {
                throw error(policyPath, "identity override " + override.getKey()
                    + " references missing profile '" + override.getValue() + "'");
            }
        }
        return new AdmissionPolicySnapshot(schema, defaultProfile, profiles, overrides, releaseTrust);
    }

    private static AdmissionProfile parseProfile(
        String profileId,
        Object rawValue,
        ArtifactCatalog catalog,
        Path path
    ) throws AdmissionPolicyException {
        String context = "profiles." + profileId;
        Map<String, Object> map = map(rawValue, path, context);
        rejectUnknown(map, PROFILE_KEYS, path, context);
        int priority = integer(map, "priority", path, context);
        if (priority < -1_000_000 || priority > 1_000_000) {
            throw error(path, context + ".priority must be between -1000000 and 1000000");
        }

        Map<String, Object> clientMap = map(required(map, "clients", path, context), path, context + ".clients");
        rejectUnknown(clientMap, CLIENT_KEYS, path, context + ".clients");
        EnumMap<ClientClassification, ClientAction> actions = new EnumMap<>(ClientClassification.class);
        actions.put(ClientClassification.BEDROCK,
            enumValue(clientMap, "bedrock", ClientAction.class, path, context + ".clients"));
        actions.put(ClientClassification.JAVA_VANILLA,
            enumValue(clientMap, "vanilla", ClientAction.class, path, context + ".clients"));
        actions.put(ClientClassification.JAVA_OPTIFINE,
            enumValue(clientMap, "optifine", ClientAction.class, path, context + ".clients"));
        actions.put(ClientClassification.JAVA_FABRIC,
            enumValue(clientMap, "fabric", ClientAction.class, path, context + ".clients"));
        actions.put(ClientClassification.JAVA_UNKNOWN,
            enumValue(clientMap, "unknown", ClientAction.class, path, context + ".clients"));

        Map<String, Object> brands = map(required(map, "unknown-brands", path, context),
            path, context + ".unknown-brands");
        rejectUnknown(brands, UNKNOWN_BRAND_KEYS, path, context + ".unknown-brands");
        BrandRuleMode brandMode = enumValue(brands, "mode", BrandRuleMode.class, path, context + ".unknown-brands");
        List<Object> rawBrandList = list(required(brands, "brands", path, context + ".unknown-brands"),
            path, context + ".unknown-brands.brands");
        if (rawBrandList.size() > MAX_BRAND_RULES) {
            throw error(path, context + ".unknown-brands.brands exceeds " + MAX_BRAND_RULES + " entries");
        }
        LinkedHashSet<String> brandRules = new LinkedHashSet<>();
        for (int i = 0; i < rawBrandList.size(); i++) {
            String normalized = BrandClassifier.normalize(scalarString(rawBrandList.get(i), path,
                context + ".unknown-brands.brands[" + i + "]"));
            if (normalized.isEmpty()) throw error(path, context + ".unknown-brands.brands[" + i + "] is blank");
            if (!brandRules.add(normalized)) {
                throw error(path, context + ".unknown-brands.brands contains duplicate normalized value '"
                    + normalized + "'");
            }
        }

        final AdmissionPolicy clientPolicy;
        try {
            clientPolicy = new AdmissionPolicy(actions, new UnknownBrandPolicy(brandMode, brandRules));
        } catch (IllegalArgumentException ex) {
            throw error(path, context + " client policy invalid: " + ex.getMessage());
        }
        ModPolicy modPolicy = parseMods(profileId, required(map, "mods", path, context), catalog, path);
        return new AdmissionProfile(profileId, priority, clientPolicy, modPolicy);
    }

    private static ModPolicy parseMods(
        String profileId,
        Object rawValue,
        ArtifactCatalog catalog,
        Path path
    ) throws AdmissionPolicyException {
        String context = "profiles." + profileId + ".mods";
        Map<String, Object> map = map(rawValue, path, context);
        rejectUnknown(map, MODS_KEYS, path, context);
        ModPolicyMode mode = enumValue(map, "mode", ModPolicyMode.class, path, context);

        Map<String, Object> origins = map(required(map, "origins", path, context), path, context + ".origins");
        rejectUnknown(origins, ORIGIN_KEYS, path, context + ".origins");
        OriginPolicyAction directory = enumValue(origins, "directory", OriginPolicyAction.class, path, context + ".origins");
        OriginPolicyAction mixed = enumValue(origins, "mixed-or-unknown", OriginPolicyAction.class, path, context + ".origins");

        List<Object> rawBaseline = list(required(map, "baseline", path, context), path, context + ".baseline");
        LinkedHashSet<String> baseline = new LinkedHashSet<>();
        for (int i = 0; i < rawBaseline.size(); i++) {
            String modId = normalizedModId(scalarString(rawBaseline.get(i), path, context + ".baseline[" + i + "]"), path);
            if (!baseline.add(modId)) throw error(path, context + ".baseline contains duplicate mod id '" + modId + "'");
        }

        Map<String, Object> rawRequired = map(required(map, "required", path, context), path, context + ".required");
        Map<String, Object> rawRules = map(required(map, "rules", path, context), path, context + ".rules");
        if (rawRequired.size() + rawRules.size() > MAX_RULES_PER_PROFILE) {
            throw error(path, context + " exceeds " + MAX_RULES_PER_PROFILE + " required+explicit rules");
        }

        LinkedHashMap<String, RequiredModRule> requiredByMod = new LinkedHashMap<>();
        HashSet<String> allRuleIds = new HashSet<>();
        for (Map.Entry<String, Object> raw : rawRequired.entrySet()) {
            String ruleId = normalizedRuleId(raw.getKey(), path);
            if (!allRuleIds.add(ruleId)) throw error(path, context + " has duplicate rule id '" + ruleId + "'");
            Map<String, Object> ruleMap = map(raw.getValue(), path, context + ".required." + ruleId);
            rejectUnknown(ruleMap, REQUIRED_KEYS, path, context + ".required." + ruleId);
            String modId = normalizedModId(string(ruleMap, "mod", path, context + ".required." + ruleId), path);
            List<ArtifactAcceptance> acceptances = parseAcceptances(
                ruleMap.get("accept"), modId, catalog, path, context + ".required." + ruleId + ".accept");
            RequiredModRule rule = new RequiredModRule(ruleId, modId, acceptances);
            if (requiredByMod.putIfAbsent(modId, rule) != null) {
                throw error(path, context + ".required has multiple rules for mod '" + modId + "'");
            }
        }

        LinkedHashMap<String, ModRule> rulesByMod = new LinkedHashMap<>();
        for (Map.Entry<String, Object> raw : rawRules.entrySet()) {
            String ruleId = normalizedRuleId(raw.getKey(), path);
            if (!allRuleIds.add(ruleId)) throw error(path, context + " has duplicate rule id '" + ruleId + "'");
            Map<String, Object> ruleMap = map(raw.getValue(), path, context + ".rules." + ruleId);
            rejectUnknown(ruleMap, RULE_KEYS, path, context + ".rules." + ruleId);
            String modId = normalizedModId(string(ruleMap, "mod", path, context + ".rules." + ruleId), path);
            ModRuleAction action = enumValue(ruleMap, "action", ModRuleAction.class, path, context + ".rules." + ruleId);
            List<ArtifactAcceptance> acceptances = parseAcceptances(
                ruleMap.get("accept"), modId, catalog, path, context + ".rules." + ruleId + ".accept");
            final ModRule rule;
            try {
                rule = new ModRule(ruleId, modId, action, acceptances);
            } catch (IllegalArgumentException ex) {
                throw error(path, context + ".rules." + ruleId + " invalid: " + ex.getMessage());
            }
            if (rulesByMod.putIfAbsent(modId, rule) != null) {
                throw error(path, context + ".rules has multiple explicit rules for mod '" + modId + "'");
            }
        }

        for (String modId : requiredByMod.keySet()) {
            ModRule rule = rulesByMod.get(modId);
            if (rule != null && rule.action() == ModRuleAction.DENY) {
                throw error(path, context + " requires mod '" + modId + "' but also unconditionally denies it");
            }
        }
        return new ModPolicy(mode, directory, mixed, baseline, requiredByMod, rulesByMod);
    }

    private static List<ArtifactAcceptance> parseAcceptances(
        Object rawValue,
        String modId,
        ArtifactCatalog catalog,
        Path path,
        String context
    ) throws AdmissionPolicyException {
        if (rawValue == null) return List.of();
        List<Object> rawList = list(rawValue, path, context);
        if (rawList.isEmpty()) throw error(path, context + " must be omitted or contain at least one entry");
        if (rawList.size() > MAX_ACCEPTANCES_PER_RULE) {
            throw error(path, context + " exceeds " + MAX_ACCEPTANCES_PER_RULE + " entries");
        }
        ArrayList<ArtifactAcceptance> result = new ArrayList<>();
        HashSet<String> normalizedClauses = new HashSet<>();
        for (int i = 0; i < rawList.size(); i++) {
            String itemContext = context + "[" + i + "]";
            Map<String, Object> item = map(rawList.get(i), path, itemContext);
            rejectUnknown(item, ACCEPT_KEYS, path, itemContext);
            VersionPredicate version;
            try {
                version = VersionPredicate.parse(string(item, "version", path, itemContext));
            } catch (IllegalArgumentException ex) {
                throw error(path, itemContext + ".version invalid: " + ex.getMessage());
            }
            ArtifactVerification verification = enumValue(item, "verification", ArtifactVerification.class, path, itemContext);
            boolean catalogRequested = optionalBoolean(item.get("catalog"), false, path, itemContext + ".catalog");

            TreeSet<ArtifactSha256> directHashes = new TreeSet<>();
            Object rawHashes = item.get("sha256");
            if (rawHashes != null) {
                List<Object> hashes = list(rawHashes, path, itemContext + ".sha256");
                if (hashes.isEmpty()) throw error(path, itemContext + ".sha256 must not be empty when declared");
                for (int h = 0; h < hashes.size(); h++) {
                    try {
                        if (!directHashes.add(new ArtifactSha256(scalarString(hashes.get(h), path,
                            itemContext + ".sha256[" + h + "]")))) {
                            throw error(path, itemContext + ".sha256 contains a duplicate digest");
                        }
                    } catch (IllegalArgumentException ex) {
                        throw error(path, itemContext + ".sha256[" + h + "] invalid: " + ex.getMessage());
                    }
                }
            }

            TreeMap<String, Set<ArtifactSha256>> catalogMatches = new TreeMap<>();
            if (catalogRequested) {
                for (ApprovedArtifact artifact : catalog.entries()) {
                    if (artifact.modId().equals(modId) && version.matches(artifact.version())) {
                        catalogMatches.computeIfAbsent(artifact.version(), ignored -> new TreeSet<>())
                            .add(artifact.sha256());
                    }
                }
                if (catalogMatches.isEmpty()) {
                    throw error(path, itemContext + " requests catalog identity for mod '" + modId
                        + "' but artifacts.yml contains no matching catalog entry for version predicate '"
                        + version.expression() + "'");
                }
            }

            if (verification == ArtifactVerification.VERSION_ONLY && (catalogRequested || !directHashes.isEmpty())) {
                throw error(path, itemContext + " VERSION_ONLY cannot declare catalog=true or sha256");
            }
            if (verification == ArtifactVerification.HASH_REQUIRED && !catalogRequested && directHashes.isEmpty()) {
                throw error(path, itemContext + " HASH_REQUIRED requires catalog=true and/or sha256");
            }
            String signature = version.expression() + "\u0000" + verification + "\u0000" + catalogRequested
                + "\u0000" + directHashes;
            if (!normalizedClauses.add(signature)) {
                throw error(path, context + " contains a duplicate acceptance clause at index " + i);
            }
            try {
                result.add(new ArtifactAcceptance(version, verification, directHashes, catalogMatches));
            } catch (IllegalArgumentException ex) {
                throw error(path, itemContext + " invalid: " + ex.getMessage());
            }
        }
        return List.copyOf(result);
    }

    private static CerberusReleaseTrust parseCerberusReleaseTrust(Object rawValue, Path path)
        throws AdmissionPolicyException {
        if (rawValue == null) return CerberusReleaseTrust.disabled();
        Map<String, Object> map = map(rawValue, path, "cerberus-release-trust");
        rejectUnknown(map, CERBERUS_RELEASE_TRUST_KEYS, path, "cerberus-release-trust");
        boolean required = optionalBoolean(
            map.get("required"), false, path, "cerberus-release-trust.required");
        Object rawKeys = map.get("ed25519-public-keys");
        List<Object> keys = rawKeys == null ? List.of()
            : list(rawKeys, path, "cerberus-release-trust.ed25519-public-keys");
        if (keys.size() > MAX_CERBERUS_RELEASE_KEYS) {
            throw error(path, "cerberus-release-trust.ed25519-public-keys exceeds "
                + MAX_CERBERUS_RELEASE_KEYS + " keys");
        }
        ArrayList<PublicKey> parsed = new ArrayList<>();
        HashSet<String> canonical = new HashSet<>();
        for (int i = 0; i < keys.size(); i++) {
            String encoded = scalarString(keys.get(i), path,
                "cerberus-release-trust.ed25519-public-keys[" + i + "]");
            if (encoded.length() > 256) {
                throw error(path, "cerberus-release-trust.ed25519-public-keys[" + i + "] is too long");
            }
            try {
                PublicKey key = CerberusReleaseCrypto.decodePublicKey(encoded);
                String canonicalEncoding = Base64.getEncoder().encodeToString(key.getEncoded());
                if (!canonical.add(canonicalEncoding)) {
                    throw error(path, "cerberus-release-trust.ed25519-public-keys contains a duplicate key");
                }
                parsed.add(key);
            } catch (GeneralSecurityException ex) {
                throw error(path, "cerberus-release-trust.ed25519-public-keys[" + i
                    + "] is not a valid Ed25519 X.509 public key: " + ex.getMessage());
            }
        }
        try {
            return new CerberusReleaseTrust(required, parsed);
        } catch (IllegalArgumentException ex) {
            throw error(path, "cerberus-release-trust invalid: " + ex.getMessage());
        }
    }

    private static Map<UUID, String> parseOverrides(Object rawValue, Path path) throws AdmissionPolicyException {
        if (rawValue == null) return Map.of();
        Map<String, Object> raw = map(rawValue, path, "identity-overrides");
        LinkedHashMap<UUID, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            final UUID id;
            try {
                id = UUID.fromString(entry.getKey());
                if (!id.toString().equals(entry.getKey().toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("non-canonical UUID");
                }
            } catch (IllegalArgumentException ex) {
                throw error(path, "identity-overrides key '" + entry.getKey() + "' is not a canonical UUID");
            }
            String profile = normalizedProfileId(scalarString(entry.getValue(), path,
                "identity-overrides." + entry.getKey()), path);
            if (result.putIfAbsent(id, profile) != null) {
                throw error(path, "duplicate identity override for " + id);
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, Object> loadYaml(Path path) throws AdmissionPolicyException {
        final String text;
        try {
            text = SafeRegularFile.readUtf8(path, MAX_POLICY_BYTES);
        } catch (IOException ex) {
            throw new AdmissionPolicyException(
                path, "admission policy must be a stable regular non-symlink UTF-8 file: " + ex.getMessage(), ex);
        }

        LoadSettings settings = LoadSettings.builder()
            .setLabel(path.toString())
            .setSchema(new CoreSchema())
            .setAllowDuplicateKeys(false)
            .setMaxAliasesForCollections(0)
            .setCodePointLimit(MAX_POLICY_BYTES)
            .build();
        Object loaded;
        try {
            loaded = new Load(settings).loadFromString(text);
        } catch (RuntimeException ex) {
            throw new AdmissionPolicyException(path, "malformed admission policy YAML: " + ex.getMessage(), ex);
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw error(path, "admission policy root must be a YAML map");
        }
        return stringKeyMap(map, path, "root");
    }

    private static Object required(Map<String, Object> map, String key, Path path, String context)
        throws AdmissionPolicyException {
        Object value = map.get(key);
        if (value == null) throw error(path, context + " is missing required key '" + key + "'");
        return value;
    }

    private static String string(Map<String, Object> map, String key, Path path, String context)
        throws AdmissionPolicyException {
        return scalarString(required(map, key, path, context), path, context + "." + key);
    }

    private static int integer(Map<String, Object> map, String key, Path path, String context)
        throws AdmissionPolicyException {
        Object value = required(map, key, path, context);
        if (!(value instanceof Number number)) throw error(path, context + "." + key + " must be an integer");
        long longValue = number.longValue();
        if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE
            || number.doubleValue() != (double) longValue) {
            throw error(path, context + "." + key + " must be an integer");
        }
        return (int) longValue;
    }

    private static boolean optionalBoolean(Object value, boolean defaultValue, Path path, String context)
        throws AdmissionPolicyException {
        if (value == null) return defaultValue;
        if (!(value instanceof Boolean bool)) throw error(path, context + " must be true or false");
        return bool;
    }

    private static <E extends Enum<E>> E enumValue(
        Map<String, Object> map, String key, Class<E> type, Path path, String context
    ) throws AdmissionPolicyException {
        String value = string(map, key, path, context).trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            throw error(path, context + "." + key + " has unsupported value '" + value
                + "'; expected one of " + List.of(type.getEnumConstants()));
        }
    }

    private static String scalarString(Object value, Path path, String context) throws AdmissionPolicyException {
        if (!(value instanceof String string) || string.isBlank()) {
            throw error(path, context + " must be a non-blank string");
        }
        return string.trim();
    }

    private static Map<String, Object> map(Object value, Path path, String context) throws AdmissionPolicyException {
        if (!(value instanceof Map<?, ?> raw)) throw error(path, context + " must be a YAML map");
        return stringKeyMap(raw, path, context);
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> raw, Path path, String context)
        throws AdmissionPolicyException {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                throw error(path, context + " contains a non-string or blank key");
            }
            if (result.putIfAbsent(key, entry.getValue()) != null) {
                throw error(path, context + " contains duplicate key '" + key + "'");
            }
        }
        return result;
    }

    private static List<Object> list(Object value, Path path, String context) throws AdmissionPolicyException {
        if (!(value instanceof List<?> raw)) throw error(path, context + " must be a YAML list");
        return new ArrayList<>(raw);
    }

    private static void rejectUnknown(Map<String, Object> map, Set<String> allowed, Path path, String context)
        throws AdmissionPolicyException {
        for (String key : map.keySet()) {
            if (!allowed.contains(key)) throw error(path, context + " contains unsupported key '" + key + "'");
        }
    }

    private static String normalizedProfileId(String value, Path path) throws AdmissionPolicyException {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!PROFILE_ID.matcher(normalized).matches()) {
            throw error(path, "profile id '" + value + "' must match " + PROFILE_ID.pattern());
        }
        return normalized;
    }

    private static String normalizedRuleId(String value, Path path) throws AdmissionPolicyException {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!RULE_ID.matcher(normalized).matches()) {
            throw error(path, "rule id '" + value + "' must match " + RULE_ID.pattern());
        }
        return normalized;
    }

    private static String normalizedModId(String value, Path path) throws AdmissionPolicyException {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!MOD_ID.matcher(normalized).matches()) {
            throw error(path, "mod id '" + value + "' must match " + MOD_ID.pattern());
        }
        return normalized;
    }

    private static AdmissionPolicyException error(Path path, String message) {
        return new AdmissionPolicyException(path, message);
    }
}
