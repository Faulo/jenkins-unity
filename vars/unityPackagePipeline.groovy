import net.slothsoft.jenkins.unity.PreparedUnityPackage
import net.slothsoft.jenkins.unity.UnityPackagePipelineOptions

void call(Closure body) {
    def args = [:]
    def configuredBody = body.rehydrate(args, body.owner, body.thisObject)
    configuredBody.resolveStrategy = Closure.DELEGATE_FIRST
    configuredBody()
    call(UnityPackagePipelineOptions.fromMap(args))
}

void call(Map args) {
    call(UnityPackagePipelineOptions.fromMap(args))
}

void call(UnityPackagePipelineOptions pipelineOptions) {
    PreparedUnityPackage preparedPackage

    pipeline {
        agent none

        options {
            disableConcurrentBuilds()
            disableResume()
            disableRestartFromStage()
            skipDefaultCheckout()
        }

        stages {
            stage('Prepare') {
                agent {
                    docker {
                        label "${pipelineOptions.prepareAgent}"
                        image "${pipelineOptions.prepareDockerImage}"
                        args "${pipelineOptions.prepareDockerArgs}"
                    }
                }
                steps {
                    checkout scm
                    script {
                        preparedPackage = prepareUnityPackage(pipelineOptions.packageOptions)
                    }
                }
            }

            stage('Test') {
                failFast false
                matrix {
                    axes {
                        axis {
                            name 'OS'
                            values 'linux', 'windows'
                        }
                    }
                    agent {
                        label "${pipelineOptions.unityAgents[OS]}"
                    }
                    stages {
                        stage('Unity package') {
                            steps {
                                script {
                                    def container = pipelineOptions.unityContainers[OS]
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
                    }
                }
            }

            stage('Publish') {
                when {
                    beforeAgent true
                    expression {
                        preparedPackage != null && currentBuild.currentResult == 'SUCCESS'
                    }
                }
                agent {
                    docker {
                        label "${pipelineOptions.publishAgent}"
                        image "${pipelineOptions.publishDockerImage}"
                        args "${pipelineOptions.publishDockerArgs}"
                    }
                }
                steps {
                    script {
                        publishUnityPackage(preparedPackage)
                    }
                }
            }
        }

        post {
            always {
                script {
                    if (preparedPackage != null) {
                        reportUnityPackage(preparedPackage)
                    }
                }
            }
        }
    }
}
