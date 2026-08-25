import net.slothsoft.jenkins.unity.PreparedUnityPackage
import net.slothsoft.jenkins.unity.InstalledUnityPackage
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
        stage("Package: ${pipelineOptions.packageOptions.packageId ?: 'Unity package'}") {
            node(pipelineOptions.prepareAgent) {
                docker.image(pipelineOptions.prepareImage).inside(pipelineOptions.prepareArgs) {
                    checkout scm
                    preparedPackage = prepareUnityPackage(pipelineOptions.packageOptions)
                }
            }
        }

        if (pipelineOptions.unityAgents) {
            def agentNames = new ArrayList(pipelineOptions.unityAgents.keySet())
            for (int index = 0; index < agentNames.size(); index++) {
                def isFirstAgent = index == 0
                if ((isFirstAgent && hasSingletonWork(preparedPackage)) || preparedPackage.options.testUnity) {
                    def name = agentNames[index]
                    runOnAgent(name, pipelineOptions.unityAgents[name], preparedPackage, isFirstAgent)
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

private void runOnAgent(String name, String agent, PreparedUnityPackage preparedPackage, boolean isFirstAgent) {
    stage("Agent: ${name}") {
        node(agent) {
            def options = preparedPackage.options
            InstalledUnityPackage installedPackage
            try {
                stage('Build: Unity package') {
                    installedPackage = installUnityPackage(preparedPackage)
                }
                if (isFirstAgent) {
                    if (options.testChangelog) {
                        stage("Test: ${displayName(options.changelogLocation)}") {
                            testUnityPackage(installedPackage)
                        }
                    }
                    if (options.testFormatting) {
                        stage('Build: C# solution') {
                            buildUnityProject(installedPackage, 'solution')
                        }
                        stage("Test: ${displayName(options.formattingLocation)}") {
                            testUnityProject(installedPackage, 'formatting')
                        }
                    }
                    if (options.buildDocumentation) {
                        stage('Build: DocFX documentation') {
                            buildUnityProject(installedPackage, 'documentation')
                        }
                    }
                }
                if (options.testUnity) {
                    stage("Test: Unity (${options.unityTestModes.join(' ')})") {
                        testUnityProject(installedPackage, 'unity')
                    }
                }
            } finally {
                deleteUnityProject(installedPackage)
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
