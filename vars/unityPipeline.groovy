def call(body) {
    // evaluate the body block, and collect configuration into the object
    def args= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = args
    body()
    
    pipeline {
        agent any
        
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
            
            // which executables to create
            BUILD_FOR_WINDOWS = "${args.BUILD_FOR_WINDOWS}"
            BUILD_FOR_LINUX = "${args.BUILD_FOR_LINUX}"
            BUILD_FOR_MAC = "${args.BUILD_FOR_MAC}"
            BUILD_FOR_WEBGL = "${args.BUILD_FOR_WEBGL}"
            BUILD_FOR_ANDROID = "${args.BUILD_FOR_ANDROID}"
            
            // which platforms to deploy to
            DEPLOY_TO_STEAM = "${args.DEPLOY_TO_STEAM}"
            DEPLOY_TO_ITCH = "${args.DEPLOY_TO_ITCH}"
            
            // configration for deploying to steam
            STEAM_ID = "${args.STEAM_ID}"
            STEAM_DEPOTS = "${args.STEAM_DEPOTS}"
            STEAM_BRANCH = env.BRANCH_NAME.replace("\\", "-")
            
            // configuration for deploying to itch
            ITCH_ID = "${args.ITCH_ID}"
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
                        anyOf {
                            environment name: 'TEST_MODES', value: ''
                            environment name: 'TEST_MODES', value: '0'
                        }
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
                        when {
                            environment name: 'BUILD_FOR_WINDOWS', value: '1'
                        }
                        steps {
                            dir(env.BUILDS) {
                                sh '$COMPOSE_UNITY unity-build "$PROJECT" "$BUILDS/build-windows" windows 1>"$REPORTS/build-windows.xml"'
                                sh 'zip -r build-windows.zip build-windows'
                            }
                        }
                    }
                    stage('Build for: Linux') {
                        when {
                            environment name: 'BUILD_FOR_LINUX', value: '1'
                        }
                        steps {
                            dir(env.BUILDS) {
                                sh '$COMPOSE_UNITY unity-build "$PROJECT" "$BUILDS/build-linux" linux 1>"$REPORTS/build-linux.xml"'
                                sh 'zip -r build-linux.zip build-linux'
                            }
                        }
                    }
                    stage('Build for: Mac') {
                        when {
                            environment name: 'BUILD_FOR_MAC', value: '1'
                        }
                        steps {
                            dir(env.BUILDS) {
                                sh '$COMPOSE_UNITY unity-build "$PROJECT" "$BUILDS/build-mac" mac 1>"$REPORTS/build-mac.xml"'
                                sh 'zip -r build-mac.zip build-mac'
                            }
                        }
                    }
                    stage('Build for: WebGL') {
                        when {
                            environment name: 'BUILD_FOR_WEBGL', value: '1'
                        }
                        steps {
                            dir(env.BUILDS) {
                                sh '$COMPOSE_UNITY unity-module-install "$PROJECT" webgl 1>"$REPORTS/install-webgl.xml"'
                                sh '$COMPOSE_UNITY unity-method "$PROJECT" Slothsoft.UnityExtensions.Editor.Build.WebGL "$BUILDS/build-webgl" 1>"$REPORTS/build-webgl.xml"'
                                sh 'zip -r build-webgl.zip build-webgl'
                            }
                        }
                        post {
                            success {
                                publishHTML([
                                   allowMissing: false,
                                   alwaysLinkToLastBuild: false,
                                   keepAll: false,
                                   reportDir: 'builds/build-webgl',
                                   reportFiles: 'index.html',
                                   reportName: 'WebGL Build',
                                   reportTitles: '',
                                   useWrapperFileDirectly: true
                               ])
                            }
                        }
    
                    }
                    stage('Build for: Android') {
                        when {
                            environment name: 'BUILD_FOR_ANDROID', value: '1'
                        }
                        steps {
                            dir(env.BUILDS) {
                                sh '$COMPOSE_UNITY unity-module-install "$PROJECT" android 1>"$REPORTS/install-android.xml"'
                                sh '$COMPOSE_UNITY unity-method "$PROJECT" Slothsoft.UnityExtensions.Editor.Build.Android "$BUILDS/build-android.apk" 1>"$REPORTS/build-android.xml"'
                                sh 'zip -r build-android.zip build-android.apk'
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
                                sh '$COMPOSE_UNITY steam-buildfile "$BUILDS" "$LOGS" $STEAM_ID $STEAM_DEPOTS $STEAM_BRANCH 1>"$BUILDS/build.vdf"'
                                script {
                                    withCredentials([usernamePassword(credentialsId: args.STEAM_CREDENTIALS, usernameVariable: 'STEAM_CREDS_USR', passwordVariable: 'STEAM_CREDS_PSW')]) {
                                        sh 'steamcmd +login $STEAM_CREDS_USR $STEAM_CREDS_PSW +run_app_build "$BUILDS/build.vdf" +quit'
                                    }
                                }
                            }
                        }
                    }
                    stage('Deploy to: itch.io') {
                        when {
                            environment name: 'DEPLOY_TO_ITCH', value: '1'
                        }
                        steps {
                            script {
                                withCredentials([string(credentialsId: args.ITCH_CREDENTIALS, variable: 'BUTLER_API_KEY')]) {
                                    stages {
                                        stage('Deploy for: Windows') {
                                            when {
                                                environment name: 'BUILD_FOR_WINDOWS', value: '1'
                                            }
                                            steps {
                                                dir(env.BUILDS) {
                                                    sh 'butler push build-windows $ITCH_ID:windows-x64'
                                                }
                                            }
                                        }
                                        stage('Deploy for: Linux') {
                                            when {
                                                environment name: 'BUILD_FOR_LINUX', value: '1'
                                            }
                                            steps {
                                                dir(env.BUILDS) {
                                                    sh 'butler push build-linux $ITCH_ID:linux-x64'
                                                }
                                            }
                                        }
                                        stage('Deploy for: Mac') {
                                            when {
                                                environment name: 'BUILD_FOR_MAC', value: '1'
                                            }
                                            steps {
                                                dir(env.BUILDS) {
                                                    sh 'butler push build-mac $ITCH_ID:mac-x64'
                                                }
                                            }
                                        }
                                        stage('Deploy for: WebGL') {
                                            when {
                                                environment name: 'BUILD_FOR_WEBGL', value: '1'
                                            }
                                            steps {
                                                dir(env.BUILDS) {
                                                    sh 'butler push build-webgl $ITCH_ID:html'
                                                }
                                            }
                                        }
                                        stage('Deploy for: Android') {
                                            when {
                                                environment name: 'BUILD_FOR_ANDROID', value: '1'
                                            }
                                            steps {
                                                dir(env.BUILDS) {
                                                    sh 'butler push build-android.apk $ITCH_ID:android'
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
        }
        
        post {
            cleanup {
                junit "reports/*.xml"
            }
        }
    }
}