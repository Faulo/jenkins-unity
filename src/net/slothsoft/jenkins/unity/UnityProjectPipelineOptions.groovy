package net.slothsoft.jenkins.unity

import com.cloudbees.groovy.cps.NonCPS

final class UnityProjectPipelineOptions implements Serializable {
    private static final long serialVersionUID = 1L

    static final Map<String, Object> INFRASTRUCTURE_DEFAULTS = Collections.unmodifiableMap([
        UNITY_AGENT: 'compose-unity',
    ])

    final UnityProjectOptions projectOptions
    final String unityAgent

    @NonCPS
    static UnityProjectPipelineOptions fromMap(Map values = [:]) {
        def infrastructureValues = values.findAll { key, ignored -> INFRASTRUCTURE_DEFAULTS.containsKey(key) }
        def projectValues = values.findAll { key, ignored -> !INFRASTRUCTURE_DEFAULTS.containsKey(key) }
        new UnityProjectPipelineOptions(UnityProjectOptions.fromMap(projectValues), infrastructureValues)
    }

    UnityProjectPipelineOptions(UnityProjectOptions projectOptions, Map infrastructureValues = INFRASTRUCTURE_DEFAULTS) {
        this.projectOptions = projectOptions
        def values = UnityPackageConfig.normalize(infrastructureValues, INFRASTRUCTURE_DEFAULTS)
        unityAgent = UnityPackageConfig.stringValue(values, 'UNITY_AGENT')
        if (!unityAgent) {
            throw new IllegalArgumentException('UNITY_AGENT must not be empty')
        }
    }
}
