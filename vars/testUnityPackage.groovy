import com.cloudbees.groovy.cps.NonCPS
import net.slothsoft.jenkins.unity.PreparedUnityPackage
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

void call(PreparedUnityPackage preparedPackage) {
    requirePreparedPackage(preparedPackage)
    def options = preparedPackage.options
    if (options.testChangelog) {
        call(preparedPackage, 'changelog')
    }
    if (options.testFormatting) {
        call(preparedPackage, 'formatting')
    }
    if (options.buildDocumentation) {
        call(preparedPackage, 'documentation')
    }
    if (options.testUnity) {
        call(preparedPackage, 'unity')
    }
}

void call(PreparedUnityPackage preparedPackage, String operation) {
    requirePreparedPackage(preparedPackage)
    try {
        switch (operation) {
            case 'changelog':
                testChangelog(preparedPackage)
                break
            case 'formatting':
                testFormatting(preparedPackage)
                break
            case 'documentation':
                buildDocumentation(preparedPackage)
                break
            case 'unity':
                testUnity(preparedPackage)
                break
            default:
                throw new IllegalArgumentException("Unknown Unity package test operation '${operation}'")
        }
    } catch (FlowInterruptedException e) {
        currentBuild.result = e.result
        throw e
    }
}

private void testChangelog(PreparedUnityPackage preparedPackage) {
    withSource(preparedPackage) { String packageDirectory, String ignoredWorkDirectory ->
        def options = preparedPackage.options
        dir(packageDirectory) {
            if (!fileExists(options.changelogLocation)) {
                unstable "Changelog at '${options.changelogLocation}' is missing."
                return
            }

            def changelogContent = readFile(options.changelogLocation)
            def context = preparedPackage.context
            def validChangelog = containsDatedVersion(changelogContent, context.version)
            if (!context.release) {
                validChangelog = validChangelog || containsDatedVersion(changelogContent, context.stableVersion)
            }
            if (!validChangelog) {
                unstable "${options.changelogLocation} does not contain a dated entry for ${context.version}${context.release ? '' : " or ${context.stableVersion}"}."
            }
        }
    }
}

private void testFormatting(PreparedUnityPackage preparedPackage) {
    withUnityProject(preparedPackage, true, true) { String projectDirectory, String reportsDirectory ->
        callDotnetFormat("${projectDirectory}/project.sln", reportsDirectory, preparedPackage.options.formattingExclusions.join(' '))
    }
}

private void buildDocumentation(PreparedUnityPackage preparedPackage) {
    catchError(stageResult: 'FAILURE', buildResult: 'UNSTABLE', catchInterruptions: false) {
        withUnityProject(preparedPackage, true, false) { String projectDirectory, String ignoredReportsDirectory ->
            dir("${projectDirectory}/.Documentation") {
                deleteDir()
                callUnity "unity-documentation '${projectDirectory}'"
                callDocFX(preparedPackage.context.packageId)
            }
        }
    }
}

private void testUnity(PreparedUnityPackage preparedPackage) {
    withUnityProject(preparedPackage, false, false) { String projectDirectory, String reportsDirectory ->
        dir(reportsDirectory) {
            callUnity "unity-tests '${projectDirectory}' ${preparedPackage.options.unityTestModes.join(' ')}", 'tests.xml'
            junit(testResults: 'tests.xml', allowEmptyResults: true)
        }
    }
}

private void withUnityProject(PreparedUnityPackage preparedPackage, boolean createSolution, boolean restoreFormatting, Closure body) {
    withSource(preparedPackage) { String packageDirectory, String workDirectory ->
        def projectDirectory = "${workDirectory}/project"
        def reportsDirectory = "${workDirectory}/reports"
        def options = preparedPackage.options
        def credentials = []
        def forwardedEnvironment = []
        if (options.unityCredentialsId) {
            credentials << usernamePassword(credentialsId: options.unityCredentialsId, usernameVariable: 'UNITY_CREDENTIALS_USR', passwordVariable: 'UNITY_CREDENTIALS_PSW')
            forwardedEnvironment.addAll(['UNITY_CREDENTIALS_USR', 'UNITY_CREDENTIALS_PSW'])
        }
        if (options.emailCredentialsId) {
            credentials << usernamePassword(credentialsId: options.emailCredentialsId, usernameVariable: 'EMAIL_CREDENTIALS_USR', passwordVariable: 'EMAIL_CREDENTIALS_PSW')
            forwardedEnvironment.addAll(['EMAIL_CREDENTIALS_USR', 'EMAIL_CREDENTIALS_PSW'])
        }
        if (options.unityManifestCredentialsId) {
            credentials << file(credentialsId: options.unityManifestCredentialsId, variable: 'UNITY_EMPTY_MANIFEST')
            forwardedEnvironment << 'UNITY_EMPTY_MANIFEST'
        }

        withCredentials(credentials) {
            def existingEnvironment = (env.JENKINS_UNITY_ENV ?: '').tokenize(':')
            def environmentNames = (existingEnvironment + forwardedEnvironment).findAll { it }.unique()
            withEnv(["JENKINS_UNITY_ENV=${environmentNames.join(':')}"]) {
                withUnity {
                    dir(reportsDirectory) {
                        callUnity "unity-package-install '${packageDirectory}' '${projectDirectory}'", 'package-install.xml'
                        junit(testResults: 'package-install.xml')
                    }

                    if (restoreFormatting) {
                        dir(projectDirectory) {
                            unstash preparedPackage.configurationStash
                            if (!fileExists(options.formattingLocation)) {
                                error "Formatting configuration '${options.formattingLocation}' does not exist in the prepared source."
                            }
                            if (options.formattingLocation != '.editorconfig') {
                                writeFile(file: '.editorconfig', text: readFile(options.formattingLocation))
                            }
                        }
                    }

                    if (createSolution) {
                        dir(reportsDirectory) {
                            callUnity "unity-method '${projectDirectory}' Slothsoft.UnityExtensions.Editor.Build.Solution", 'build-solution.xml'
                            junit(testResults: 'build-solution.xml')
                        }
                    }

                    body(projectDirectory, reportsDirectory)
                }
            }
        }
    }
}

private void withSource(PreparedUnityPackage preparedPackage, Closure body) {
    def invocationId = UUID.randomUUID().toString()
    def temporaryRoot = pwd(tmp: true)
    def workDirectory = "${temporaryRoot}/unity-package-${preparedPackage.executionId}-${invocationId}"
    def packageDirectory = "${workDirectory}/package"
    try {
        dir(workDirectory) {
            deleteDir()
            dir('package') {
                unstash preparedPackage.packageStash
            }
        }
        body(packageDirectory, workDirectory)
    } finally {
        dir(workDirectory) {
            deleteDir()
        }
    }
}

private void requirePreparedPackage(PreparedUnityPackage preparedPackage) {
    if (!preparedPackage) {
        throw new IllegalArgumentException('preparedPackage must not be null')
    }
}

@NonCPS
private boolean containsDatedVersion(String changelog, String version) {
    def expected = ~/^## \[${java.util.regex.Pattern.quote(version)}\] - \d{4}-\d{2}-\d{2}$/
    changelog.readLines().any { line -> line ==~ expected }
}
