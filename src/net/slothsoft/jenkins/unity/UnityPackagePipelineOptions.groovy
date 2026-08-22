package net.slothsoft.jenkins.unity

import com.cloudbees.groovy.cps.NonCPS

final class UnityPackagePipelineOptions implements Serializable {
    private static final long serialVersionUID = 1L

    static final Map<String, Object> INFRASTRUCTURE_DEFAULTS = Collections.unmodifiableMap([
        PREPARE_AGENT: 'npm',
        PREPARE_DOCKER_IMAGE: 'node:22-bookworm-slim',
        PREPARE_DOCKER_ARGS: '',
        PUBLISH_AGENT: 'npm',
        PUBLISH_DOCKER_IMAGE: 'node:22-bookworm-slim',
        PUBLISH_DOCKER_ARGS: '',
        UNITY_AGENTS: [linux: 'linux && compose-unity', windows: 'windows && compose-unity'],
        UNITY_CONTAINERS: [linux: '', windows: ''],
    ])

    final UnityPackageOptions packageOptions
    final String prepareAgent
    final String prepareDockerImage
    final String prepareDockerArgs
    final String publishAgent
    final String publishDockerImage
    final String publishDockerArgs
    final Map<String, String> unityAgents
    final Map<String, String> unityContainers

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
        prepareDockerImage = UnityPackageConfig.stringValue(values, 'PREPARE_DOCKER_IMAGE')
        prepareDockerArgs = UnityPackageConfig.stringValue(values, 'PREPARE_DOCKER_ARGS')
        publishAgent = UnityPackageConfig.stringValue(values, 'PUBLISH_AGENT')
        publishDockerImage = UnityPackageConfig.stringValue(values, 'PUBLISH_DOCKER_IMAGE')
        publishDockerArgs = UnityPackageConfig.stringValue(values, 'PUBLISH_DOCKER_ARGS')
        unityAgents = UnityPackageConfig.stringMap(values, 'UNITY_AGENTS', ['linux', 'windows'])
        unityContainers = UnityPackageConfig.stringMap(values, 'UNITY_CONTAINERS', ['linux', 'windows'])

        if (!prepareAgent || !prepareDockerImage || !publishAgent || !publishDockerImage || unityAgents.any { ignored, label -> !label }) {
            throw new IllegalArgumentException('Agent labels and Docker images must not be empty')
        }
    }
}
