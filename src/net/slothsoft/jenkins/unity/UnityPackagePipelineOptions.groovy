package net.slothsoft.jenkins.unity

import com.cloudbees.groovy.cps.NonCPS

final class UnityPackagePipelineOptions implements Serializable {
    private static final long serialVersionUID = 1L

    static final Map<String, Object> INFRASTRUCTURE_DEFAULTS = Collections.unmodifiableMap([
        PREPARE_AGENT: 'linux && docker',
        PREPARE_IMAGE: 'node:slim',
        PREPARE_ARGS: '',

        PUBLISH_AGENT: 'linux && docker',
        PUBLISH_IMAGE: 'node:slim',
        PUBLISH_ARGS: '',

        UNITY_AGENTS: [Linux: 'linux && compose-unity', Windows: 'windows && compose-unity'],
    ])

    final UnityPackageOptions packageOptions
    final String prepareAgent
    final String prepareImage
    final String prepareArgs
    final String publishAgent
    final String publishImage
    final String publishArgs
    final Map<String, String> unityAgents

    @NonCPS
    static UnityPackagePipelineOptions fromMap(Map values = [:]) {
        def infrastructureValues = values.findAll { key, ignored -> INFRASTRUCTURE_DEFAULTS.containsKey(key) }
        def packageValues = values.findAll { key, ignored -> !INFRASTRUCTURE_DEFAULTS.containsKey(key) }
        def normalizedInfrastructure = UnityPackageConfig.normalize(infrastructureValues, INFRASTRUCTURE_DEFAULTS)
        new UnityPackagePipelineOptions(UnityPackageOptions.fromMap(packageValues), normalizedInfrastructure)
    }

    UnityPackagePipelineOptions(UnityPackageOptions packageOptions, Map infrastructureValues = INFRASTRUCTURE_DEFAULTS) {
        this.packageOptions = packageOptions
        def values = UnityPackageConfig.normalize(infrastructureValues, INFRASTRUCTURE_DEFAULTS)
        prepareAgent = UnityPackageConfig.stringValue(values, 'PREPARE_AGENT')
        prepareImage = UnityPackageConfig.stringValue(values, 'PREPARE_IMAGE')
        prepareArgs = UnityPackageConfig.stringValue(values, 'PREPARE_ARGS')
        publishAgent = UnityPackageConfig.stringValue(values, 'PUBLISH_AGENT')
        publishImage = UnityPackageConfig.stringValue(values, 'PUBLISH_IMAGE')
        publishArgs = UnityPackageConfig.stringValue(values, 'PUBLISH_ARGS')
        unityAgents = UnityPackageConfig.stringMap(values, 'UNITY_AGENTS')

        if (!prepareAgent || !prepareImage || !publishAgent || !publishImage) {
            throw new IllegalArgumentException('Agent labels and Docker images must not be empty')
        }
        if (unityAgents.any { name, label -> !name || !label }) {
            throw new IllegalArgumentException('UNITY_AGENTS must not contain empty names or labels')
        }
    }
}
