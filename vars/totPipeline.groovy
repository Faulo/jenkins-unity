def call(body) {
    // evaluate the body block, and collect configuration into the object
    def args= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = args
    body()

    pipeline {
        agent any

        options {
            disableConcurrentBuilds()
            disableResume()
        }

        environment {
            // define unity project location relative to repository
            PROJECT = "${env.WORKSPACE}/${args.PROJECT_LOCATION}"

            // use auto-versioning based on tags+commits
            PROJECT_AUTOVERSION = "${args.PROJECT_AUTOVERSION}"

            // temporary folders
            REPORTS = "${env.WORKSPACE}/reports"
            BUILDS = "${env.WORKSPACE}/builds"
            LOGS = "${env.WORKSPACE}/logs"

            // which Unity Test Runner modes to execute
            TEST_MODES = "${args.TEST_MODES}"

            // which platforms to deploy to
            DEPLOY_TO_STEAM = "${args.DEPLOY_TO_STEAM}"

            // configration for deploying to steam
            STEAM_ID = "${args.STEAM_ID}"
            STEAM_DEPOTS = "${args.STEAM_DEPOTS}"
            STEAM_BRANCH = "${args.STEAM_BRANCH}"

            TOT_ASSET = "${args.TOT_ASSET}"
            TOT_EXE = "${args.TOT_EXE}"
        }

        stages {
            stage('Folder (re)creation') {
                steps {
                    sh 'rm -rf "$REPORTS" "$BUILDS" "$LOGS"'
                    sh 'mkdir -p "$REPORTS" "$BUILDS" "$LOGS"'
                }
            }
            stage('Versioning') {
                when {
                    anyOf {
                        environment name: 'PROJECT_AUTOVERSION', value: 'git'
                        environment name: 'PROJECT_AUTOVERSION', value: 'plastic'
                    }
                }
                environment {
                    PROJECT_VERSION = sh(script: "$COMPOSE_UNITY autoversion '$PROJECT_AUTOVERSION' '$WORKSPACE'", returnStdout: true).trim()
                }
                steps {
                    sh '$COMPOSE_UNITY unity-project-version "$PROJECT" set "$PROJECT_VERSION"'
                }
            }
            stage('Testing') {
                when {
                    not {
                        environment name: 'TEST_MODES', value: ''
                    }
                }
                steps {
                    echo 'Running tests....'
                    sh '$COMPOSE_UNITY unity-tests "$PROJECT" $TEST_MODES 1>"$REPORTS/tests.xml"'
                }
            }
            stage('Delivery') {
                stages {
                    stage('Build for: Windows') {
                        steps {
                            dir(env.BUILDS) {
                                sh '$COMPOSE_UNITY unity-method "$PROJECT" "Oilcatz.Editor.Build.Asset" "$TOT_ASSET" "$BUILDS/build-windows/$TOT_EXE.exe" 1>"$REPORTS/build-windows.xml"'
                                sh 'rm -r "build-windows/${TOT_EXE}_BurstDebugInformation_DoNotShip"'
                                sh 'zip -r build-windows.zip build-windows'
                            }
                        }
                    }
                }
                post {
                    always {
                        archiveArtifacts artifacts: 'builds/*.zip'
                    }
                }
            }
            stage('Deployment') {
                when {
                    branch 'main'
                }
                stages {
                    stage('Deploy to: Steam') {
                        when {
                            environment name: 'DEPLOY_TO_STEAM', value: '1'
                        }
                        steps {
                            dir(env.BUILDS) {
                                script {
                                    sh '$COMPOSE_UNITY steam-buildfile "$BUILDS" "$LOGS" $STEAM_ID $STEAM_DEPOTS $STEAM_BRANCH 1>"$BUILDS/build.vdf"'
                                    withCredentials([usernamePassword(credentialsId: args.STEAM_CREDENTIALS, usernameVariable: 'STEAM_CREDS_USR', passwordVariable: 'STEAM_CREDS_PSW')]) {
                                        sh 'steamcmd +login $STEAM_CREDS_USR $STEAM_CREDS_PSW +run_app_build "$BUILDS/build.vdf" +quit'
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        post {
            cleanup {
                junit "reports/*.xml"
            }
        }
    }
}