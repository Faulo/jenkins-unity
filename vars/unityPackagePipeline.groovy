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
                docker.image(pipelineOptions.prepareImage).inside(pipelineOptions.prepareArgs) {
                    checkout scm
                    preparedPackage = prepareUnityPackage(pipelineOptions.packageOptions)
                }
            }
        }

        if (pipelineOptions.unityAgents) {
            stage('Test') {
                def testBranches = pipelineOptions.unityAgents.collectEntries { name, agent ->
                    [(name): {
                        testOnAgent(name, agent, preparedPackage)
                    }]
                }
                testBranches.failFast = false
                parallel(testBranches)
            }
        }

        if (currentBuild.currentResult == 'SUCCESS') {
            stage('Publish') {
                node(pipelineOptions.publishAgent) {
                    docker.image(pipelineOptions.publishImage).inside(pipelineOptions.publishArgs) {
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

private void testOnAgent(String name, String agent, PreparedUnityPackage preparedPackage) {
    node(agent) {
        stage("Unity package (${name})") {
            testUnityPackage(preparedPackage)
        }
    }
}
