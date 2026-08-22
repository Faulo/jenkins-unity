package net.slothsoft.jenkins.unity

import com.cloudbees.groovy.cps.NonCPS

final class UnityPackageConfig {
    private UnityPackageConfig() {
    }

    @NonCPS
    static Map<String, Object> normalize(Map values, Map<String, Object> defaults) {
        def unknownKeys = values.keySet() - defaults.keySet()
        if (unknownKeys) {
            throw new IllegalArgumentException("Unknown configuration keys: ${unknownKeys.sort().join(', ')}")
        }

        defaults + values
    }

    @NonCPS
    static String stringValue(Map values, String key) {
        def value = values[key]
        if (!(value instanceof CharSequence)) {
            throw new IllegalArgumentException("${key} must be a string")
        }
        value.toString()
    }

    @NonCPS
    static boolean booleanValue(Map values, String key) {
        def value = values[key]
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException("${key} must be a boolean")
        }
        value
    }

    @NonCPS
    static List<String> stringList(Map values, String key, boolean allowEmpty = true) {
        def value = values[key]
        if (!(value instanceof Collection) || value.any { !(it instanceof CharSequence) }) {
            throw new IllegalArgumentException("${key} must be a collection of strings")
        }

        def result = value.collect { it.toString() }
        if (result.any { !it }) {
            throw new IllegalArgumentException("${key} must not contain empty strings")
        }
        if (!allowEmpty && result.empty) {
            throw new IllegalArgumentException("${key} must not be empty")
        }
        Collections.unmodifiableList(result)
    }

    @NonCPS
    static Map<String, String> stringMap(Map values, String key, Collection<String> requiredKeys) {
        def value = values[key]
        if (!(value instanceof Map) || value.any { entry -> !(entry.key instanceof CharSequence) || !(entry.value instanceof CharSequence) }) {
            throw new IllegalArgumentException("${key} must be a map of strings")
        }

        def result = value.collectEntries { entry -> [(entry.key.toString()): entry.value.toString()] }
        def missingKeys = requiredKeys - result.keySet()
        def unknownKeys = result.keySet() - requiredKeys
        if (missingKeys || unknownKeys) {
            throw new IllegalArgumentException("${key} must contain exactly: ${requiredKeys.join(', ')}")
        }
        Collections.unmodifiableMap(new LinkedHashMap<String, String>(result))
    }

    @NonCPS
    static void requireRelativePath(String value, String key) {
        if (!value || value.startsWith('/') || value.startsWith('\\') || value ==~ /^[A-Za-z]:.*/ || value.tokenize('/\\').contains('..')) {
            throw new IllegalArgumentException("${key} must be a non-empty relative path without '..'")
        }
    }
}
