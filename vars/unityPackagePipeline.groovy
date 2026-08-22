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
