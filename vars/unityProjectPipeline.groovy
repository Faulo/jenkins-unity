import net.slothsoft.jenkins.unity.UnityProjectContext
import net.slothsoft.jenkins.unity.UnityProjectPipelineOptions
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def call(Object input = [:]) {
    UnityProjectPipelineOptions pipelineOptions
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
        pipelineOptions = UnityProjectPipelineOptions.fromMap(args)
    } else if (input instanceof Map) {
        pipelineOptions = UnityProjectPipelineOptions.fromMap(input)
    } else if (input instanceof UnityProjectPipelineOptions) {
        pipelineOptions = input
    } else {
        throw new IllegalArgumentException("Expected Map, Closure, or UnityProjectPipelineOptions, got ${input?.getClass()?.name ?: 'null'}")
    }

    UnityProjectContext project
    try {
        stage("Project: ${pipelineOptions.projectOptions.projectId ?: 'Unity project'}") {
            node(pipelineOptions.unityAgent) {
                checkout scm
                project = inspectProject(pipelineOptions)
                try {
                    dir(project.reportsDirectory) {
                        deleteDir()
                    }
                    runProject(project)
                } finally {
                    dir(project.reportsDirectory) {
                        deleteDir()
                    }
                }
            }
        }
    } finally {
        if (project != null) {
            report(project)
        }
    }
}

private UnityProjectContext inspectProject(UnityProjectPipelineOptions pipelineOptions) {
    def options = pipelineOptions.projectOptions
    def workspaceDirectory = normalizePath(pwd())
    def projectDirectory = "${workspaceDirectory}/${options.projectLocation}"
    def reportsDirectory = "${normalizePath(pwd(tmp: true))}/unity-project-reports"
    def branch = options.projectBranch ?: env.BRANCH_NAME ?: env.PLASTICSCM_BRANCH
    if (!branch) {
        throw new IllegalArgumentException('PROJECT_BRANCH is required when no Jenkins branch environment is available')
    }

    def projectId = options.projectId
    def version = options.projectVersion
    try {
        if (!projectId || options.autoversion || (!version && options.hasReporting())) {
            withUnityProjectEnvironment(options) {
                if (!projectId) {
                    try {
                        projectId = callUnity "unity-project-setting '${projectDirectory}' 'productName'"
                    } catch (FlowInterruptedException e) {
                        throw e
                    } catch (Throwable ignored) {
                        projectId = 'Unknown'
                    }
                }

                if (options.autoversion) {
                    version = callUnity "autoversion '${options.autoversion}' '${workspaceDirectory}'"
                    if (options.autoversionRevision) {
                        version += "+${options.autoversionRevisionPrefix}${env.BUILD_NUMBER}${options.autoversionRevisionSuffix}"
                    }
                } else if (!version && options.hasReporting()) {
                    try {
                        version = callUnity "unity-project-setting '${projectDirectory}' 'bundleVersion'"
                    } catch (FlowInterruptedException e) {
                        throw e
                    } catch (Throwable ignored) {
                        version = '?'
                    }
                }
            }
        }
    } catch (FlowInterruptedException e) {
        currentBuild.result = e.result
        throw e
    }

    new UnityProjectContext(options, projectId, version, branch.toString(), workspaceDirectory, projectDirectory, reportsDirectory)
}

private void runProject(UnityProjectContext project) {
    def options = project.options
    if (options.projectVersion || options.autoversion) {
        stage('Set: Project version') {
            setUnityProjectVersion(project)
        }
    }
    if (options.testFormatting || options.buildDocumentation) {
        stage('Build: C# solution') {
            buildUnityProject(project, 'solution')
        }
    }
    if (options.buildDocumentation) {
        stage('Build: DocFX documentation') {
            buildUnityProject(project, 'documentation')
        }
    }
    if (options.testFormatting) {
        stage("Test: ${displayName(options.formattingLocation)}") {
            testUnityProject(project, 'formatting')
        }
    }
    if (options.testUnity) {
        stage("Test: Unity (${options.unityTestModes.join(' ')})") {
            testUnityProject(project, 'unity')
        }
    }
    if (options.buildForWindows) {
        stage('Build: Windows') {
            buildUnityProject(project, 'windows')
        }
    }
    if (options.buildForLinux) {
        stage('Build: Linux') {
            buildUnityProject(project, 'linux')
        }
    }
    if (options.buildForMac) {
        stage('Build: macOS') {
            buildUnityProject(project, 'mac')
        }
    }
    if (options.buildForWebGL) {
        stage('Build: WebGL') {
            buildUnityProject(project, 'webgl')
        }
    }
    if (options.buildForAndroid) {
        stage('Build: Android') {
            buildUnityProject(project, 'android')
        }
    }

    if (options.hasPlayerBuild() && options.deploymentBranches.contains(project.branch)) {
        if (options.deployToSteam) {
            stage('Deploy: Steam') {
                deployUnityProject(project, 'steam')
            }
        }
        if (options.deployToItch) {
            stage('Deploy: itch.io') {
                deployUnityProject(project, 'itch')
            }
        }
    }
}

private void report(UnityProjectContext project) {
    def options = project.options
    if (options.reportToDiscord && shouldReport(options.discordThreshold)) {
        stage('Report: Discord') {
            reportUnityProject(project, 'discord')
        }
    }
    if (options.reportToOffice365 && shouldReport(options.office365Threshold)) {
        stage('Report: Office 365') {
            reportUnityProject(project, 'office365')
        }
    }
    if (options.reportToAdaptiveCards && shouldReport(options.adaptiveCardsThreshold)) {
        stage('Report: Adaptive Cards') {
            reportUnityProject(project, 'adaptiveCards')
        }
    }
}

private boolean shouldReport(String threshold) {
    !threshold || currentBuild.resultIsWorseOrEqualTo(threshold)
}

private String displayName(String location) {
    location.replace('\\', '/').tokenize('/').last()
}

private String normalizePath(String path) {
    path.replace('\\', '/')
}
