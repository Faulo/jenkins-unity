def testConfig = readProperties text: readTrusted('.jenkins/testProject.properties')

pipeline {
    agent none

    parameters {
        booleanParam(name: 'SKIP_WINDOWS_VALIDATION', defaultValue: false, description: 'Run integration tests without allocating Windows agents.')
    }

    options {
        disableConcurrentBuilds()
        disableResume()
        disableRestartFromStage()
    }

    stages {
        stage('Unit Tests') {
            agent {
                docker {
                    image 'maven:3-eclipse-temurin-21'
                    label 'docker && linux'
                }
            }
            steps {
                exec 'mvn --batch-mode clean test'
            }
            post {
                always {
                    junit(testResults: 'target/surefire-reports/TEST-*.xml', allowEmptyResults: false)
                }
            }
        }
        stage('Integration Tests') {
            steps {
                script {
                    for (String platform : testConfig.integrationPlatforms.tokenize(' ')) {
                        if (platform == 'windows' && params.SKIP_WINDOWS_VALIDATION) {
                            continue
                        }
                        stage(platform) {
                            node("${testConfig.integrationAgent} && ${platform}") {
                                checkout scm
                                catchError(stageResult: 'FAILURE', buildResult: 'FAILURE', catchInterruptions: false) {
                                    timeout(time: testConfig.integrationTimeoutMinutes as Integer, unit: 'MINUTES') {
                                        def tests = load(testConfig.integrationScript)
                                        tests.call(testConfig, platform)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
