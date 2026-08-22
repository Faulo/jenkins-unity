import net.slothsoft.jenkins.unity.PreparedUnityPackage
import net.slothsoft.jenkins.unity.UnityPackagePipelineOptions

def call(Object input = [:]) {
    UnityPackagePipelineOptions pipelineOptions
    if (input instanceof Closure) {
        def args = [:]
        def originalDelegate = input.delegate
        def originalResolveStrategy = input.resolveStrategy
        try {
            input.delegate = args
            input.resolveStrategy = Closure.DELEGATE_FIRST
            input()
        } finally {
            input.delegate = originalDelegate
            input.resolveStrategy = originalResolveStrategy
        }
        pipelineOptions = UnityPackagePipelineOptions.fromMap(args)
    } else if (input instanceof Map) {
        pipelineOptions = UnityPackagePipelineOptions.fromMap(input)
    } else if (input instanceof UnityPackagePipelineOptions) {
        pipelineOptions = input
    } else {
        throw new IllegalArgumentException("Expected Map, Closure, or UnityPackagePipelineOptions, got ${input?.getClass()?.name ?: 'null'}")
    }

    PreparedUnityPackage preparedPackage
    try {
        stage('Prepare') {
            node(pipelineOptions.prepareAgent) {
                docker.image(pipelineOptions.prepareDockerImage).inside(pipelineOptions.prepareDockerArgs) {
                    checkout scm
                    preparedPackage = prepareUnityPackage(pipelineOptions.packageOptions)
                }
            }
        }

        stage('Test') {
            def linuxAgent = pipelineOptions.unityAgents.linux
            def windowsAgent = pipelineOptions.unityAgents.windows
            def linuxContainer = pipelineOptions.unityContainers.linux
            def windowsContainer = pipelineOptions.unityContainers.windows
            parallel(
                linux: {
                    testOnAgent('linux', linuxAgent, linuxContainer, preparedPackage)
                },
                windows: {
                    testOnAgent('windows', windowsAgent, windowsContainer, preparedPackage)
                },
                failFast: false
            )
        }

        if (currentBuild.currentResult == 'SUCCESS') {
            stage('Publish') {
                node(pipelineOptions.publishAgent) {
                    docker.image(pipelineOptions.publishDockerImage).inside(pipelineOptions.publishDockerArgs) {
                        publishUnityPackage(preparedPackage)
                    }
                }
            }
        }
    } finally {
        if (preparedPackage != null) {
            reportUnityPackage(preparedPackage)
        }
    }
}

private void testOnAgent(String os, String agent, String container, PreparedUnityPackage preparedPackage) {
    node(agent) {
        stage("Unity package (${os})") {
            if (container) {
                withEnv(["JENKINS_UNITY_CONTAINER=${container}"]) {
                    testUnityPackage(preparedPackage)
                }
            } else {
                testUnityPackage(preparedPackage)
            }
        }
    }
}
