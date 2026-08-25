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
                def packageId = packageIdForStage(pipelineOptions)
                stage("Package: ${packageId}") {
                    preparedPackage = prepareUnityPackage(pipelineOptions.packageOptions)
                }
            }
        }

        if (pipelineOptions.unityAgents) {
            def agentNames = new ArrayList(pipelineOptions.unityAgents.keySet())
            def firstName = agentNames[0]
            if (hasSingletonWork(preparedPackage) || preparedPackage.options.testUnity) {
                testOnFirstAgent(firstName, pipelineOptions.unityAgents[firstName], preparedPackage)
            }
            if (preparedPackage.options.testUnity) {
                for (int index = 1; index < agentNames.size(); index++) {
                    def name = agentNames[index]
                    testUnityOnAgent(name, pipelineOptions.unityAgents[name], preparedPackage)
                }
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
            report(preparedPackage)
        }
    }
}

private void testOnFirstAgent(String name, String agent, PreparedUnityPackage preparedPackage) {
    stage("Agent: ${name}") {
        node(agent) {
            def options = preparedPackage.options
            if (options.testChangelog) {
                stage("Test: ${displayName(options.changelogLocation)}") {
                    testUnityPackage(preparedPackage, 'changelog')
                }
            }
            if (options.testFormatting) {
                stage("Test: ${displayName(options.formattingLocation)}") {
                    testUnityPackage(preparedPackage, 'formatting')
                }
            }
            if (options.buildDocumentation) {
                stage('Build: DocFX documentation') {
                    testUnityPackage(preparedPackage, 'documentation')
                }
            }
            if (options.testUnity) {
                stage("Test: Unity (${options.unityTestModes.join(' ')})") {
                    testUnityPackage(preparedPackage, 'unity')
                }
            }
        }
    }
}

private void testUnityOnAgent(String name, String agent, PreparedUnityPackage preparedPackage) {
    stage("Agent: ${name}") {
        node(agent) {
            stage("Test: Unity (${preparedPackage.options.unityTestModes.join(' ')})") {
                testUnityPackage(preparedPackage, 'unity')
            }
        }
    }
}

private void report(PreparedUnityPackage preparedPackage) {
    def options = preparedPackage.options
    if (options.reportToDiscord && shouldReport(options.discordThreshold)) {
        stage('Report: Discord') {
            reportUnityPackage(preparedPackage, 'discord')
        }
    }
    if (options.reportToOffice365 && shouldReport(options.office365Threshold)) {
        stage('Report: Office 365') {
            reportUnityPackage(preparedPackage, 'office365')
        }
    }
    if (options.reportToAdaptiveCards && shouldReport(options.adaptiveCardsThreshold)) {
        stage('Report: Adaptive Cards') {
            reportUnityPackage(preparedPackage, 'adaptiveCards')
        }
    }
}

private boolean hasSingletonWork(PreparedUnityPackage preparedPackage) {
    def options = preparedPackage.options
    options.testChangelog || options.testFormatting || options.buildDocumentation
}

private boolean shouldReport(String threshold) {
    !threshold || currentBuild.resultIsWorseOrEqualTo(threshold)
}

private String displayName(String location) {
    location.replace('\\', '/').tokenize('/').last()
}

private String packageIdForStage(UnityPackagePipelineOptions pipelineOptions) {
    def options = pipelineOptions.packageOptions
    if (options.packageId) {
        return options.packageId
    }
    dir(options.packageLocation) {
        readJSON(file: 'package.json').name.toString()
    }
}
