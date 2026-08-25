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
        node(pipelineOptions.prepareAgent) {
            docker.image(pipelineOptions.prepareImage).inside(pipelineOptions.prepareArgs) {
                checkout scm
                preparedPackage = prepareUnityPackage(pipelineOptions.packageOptions)
            }
        }

        if (pipelineOptions.unityAgents) {
            def agentNames = new ArrayList(pipelineOptions.unityAgents.keySet())
            for (int index = 0; index < agentNames.size(); index++) {
                def name = agentNames[index]
                testOnAgent(name, pipelineOptions.unityAgents[name], preparedPackage)
            }
        }

        if (pipelineOptions.packageOptions.publishToVerdaccio && currentBuild.currentResult == 'SUCCESS') {
            stage('Publish: Verdaccio') {
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
    stage("Agent: ${name}") {
        node(agent) {
            testUnityPackage(preparedPackage)
        }
    }
}
