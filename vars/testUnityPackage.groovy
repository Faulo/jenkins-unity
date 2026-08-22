import net.slothsoft.jenkins.unity.PreparedUnityPackage
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

void call(PreparedUnityPackage preparedPackage) {
    if (!preparedPackage) {
        throw new IllegalArgumentException('preparedPackage must not be null')
    }

    def invocationId = UUID.randomUUID().toString()
    def temporaryRoot = pwd(tmp: true)
    def workDirectory = "${temporaryRoot}/unity-package-${preparedPackage.executionId}-${invocationId}"
    def packageDirectory = "${workDirectory}/package"
    def projectDirectory = "${workDirectory}/project"
    def reportsDirectory = "${workDirectory}/reports"
    def options = preparedPackage.options

    try {
        dir(workDirectory) {
            deleteDir()
            dir('package') {
                unstash preparedPackage.packageStash
            }

            def createSolution = options.checkFormatting || options.buildDocumentation
            def createProject = createSolution || options.runUnityTests
            if (!createProject) {
                echo 'No Unity package tests are enabled.'
                return
            }

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
                        dir('reports') {
                            callUnity "unity-package-install '${packageDirectory}' '${projectDirectory}'", 'package-install.xml'
                            junit(testResults: 'package-install.xml')
                        }

                        if (options.checkFormatting) {
                            dir('project') {
                                unstash preparedPackage.configurationStash
                                if (options.editorConfigFile != '.editorconfig') {
                                    writeFile(file: '.editorconfig', text: readFile(options.editorConfigFile))
                                }
                            }
                        }

                        if (createSolution) {
                            dir('reports') {
                                callUnity "unity-method '${projectDirectory}' Slothsoft.UnityExtensions.Editor.Build.Solution", 'build-solution.xml'
                                junit(testResults: 'build-solution.xml')
                            }
                        }

                        if (options.buildDocumentation) {
                            catchError(stageResult: 'FAILURE', buildResult: 'UNSTABLE', catchInterruptions: false) {
                                dir('project/.Documentation') {
                                    deleteDir()
                                    callUnity "unity-documentation '${projectDirectory}'"
                                    callDocFX(preparedPackage.context.packageId)
                                }
                            }
                        }

                        if (options.checkFormatting) {
                            callDotnetFormat("${projectDirectory}/project.sln", reportsDirectory, options.formattingExclude.join(' '))
                        }

                        if (options.runUnityTests) {
                            dir('reports') {
                                callUnity "unity-tests '${projectDirectory}' ${options.unityTestModes.join(' ')}", 'tests.xml'
                                junit(testResults: 'tests.xml', allowEmptyResults: true)
                            }
                        }
                    }
                }
            }
        }
    } catch (FlowInterruptedException e) {
        currentBuild.result = e.result
        throw e
    } finally {
        dir(workDirectory) {
            deleteDir()
        }
    }
}
