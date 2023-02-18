def call(body) {
    def args= [
        PROJECT_LOCATION : "",
        PROJECT_AUTOVERSION : "",

        TEST_MODES : "",

        BUILD_FOR_WINDOWS : "0",
        BUILD_FOR_LINUX : "0",
        BUILD_FOR_MAC : "0",
        BUILD_FOR_WEBGL : "0",
        BUILD_FOR_ANDROID : "0",

        DEPLOY_TO_STEAM : "0",
        STEAM_ID : "",
        STEAM_DEPOTS : "",
        STEAM_BRANCH : "${env.BRANCH_NAME}".replace("\\", "-"),

        DEPLOY_TO_ITCH : "0",
        ITCH_ID : "",

        DEPLOYMENT_BRANCHES : [ "main" ],
    ]

    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = args
    body()

    def project = "$WORKSPACE/${args.PROJECT_LOCATION}"
    def reports = "$WORKSPACE/reports"
    def builds = "$WORKSPACE/builds"

    def testAny = args.TEST_MODES != ''
    def buildAny = [args.BUILD_FOR_WINDOWS, args.BUILD_FOR_LINUX, args.BUILD_FOR_MAC, args.BUILD_FOR_WEBGL, args.BUILD_FOR_ANDROID].contains('1');

    if (args.PROJECT_AUTOVERSION != "") {
        stage("Versioning") {
            def version = callUnity "autoversion '${args.PROJECT_AUTOVERSION}' '$WORKSPACE'"
            callUnity "unity-project-version '${project}' set '${version}'"
        }
    }

    try {
        sh "mkdir -p '${reports}'"

        if (testAny) {
            stage("Testing") {
                callUnity "unity-tests '${project}' ${args.TEST_MODES} 1>'${reports}/tests.xml'"
            }
        }

        if (buildAny) {
            dir('builds') {
                if (args.BUILD_FOR_WINDOWS == '1') {
                    stage('Build for: Windows') {
                        callUnity "unity-build '${project}' '${builds}/build-windows' windows 1>'${reports}/build-windows.xml'"
                        sh 'zip -r build-windows.zip build-windows'
                    }
                }

                if (args.BUILD_FOR_LINUX == '1') {
                    stage('Build for: Linux') {
                        callUnity "unity-build '${project}' '${builds}/build-linux' linux 1>'${reports}/build-linux.xml'"
                        sh 'zip -r build-linux.zip build-linux'
                    }
                }

                if (args.BUILD_FOR_MAC == '1') {
                    stage('Build for: Mac OS') {
                        callUnity "unity-build '${project}' '${builds}/build-mac' mac 1>'${reports}/build-mac.xml'"
                        sh 'zip -r build-mac.zip build-mac'
                    }
                }

                if (args.BUILD_FOR_WEBGL == '1') {
                    stage('Build for: WebGL') {
                        callUnity "unity-module-install '${project}' webgl 1>'${reports}/install-webgl.xml'"
                        callUnity "unity-method '${project}' Slothsoft.UnityExtensions.Editor.Build.WebGL '${builds}/build-webgl' 1>'${reports}/build-webgl.xml'"
                        sh 'zip -r build-webgl.zip build-webgl'
                        publishHTML([
                           allowMissing: false,
                           alwaysLinkToLastBuild: false,
                           keepAll: false,
                           reportDir: 'build-webgl',
                           reportFiles: 'index.html',
                           reportName: 'WebGL Build',
                           reportTitles: '',
                           useWrapperFileDirectly: true
                       ])
                    }
                }

                if (args.BUILD_FOR_ANDROID == '1') {
                    stage('Build for: Android') {
                        callUnity "unity-module-install '${project}' android 1>'${reports}/install-android.xml'"
                        callUnity "unity-method '${project}' Slothsoft.UnityExtensions.Editor.Build.Android '${builds}/build-android.apk' 1>'${reports}/build-android.xml'"
                        sh 'zip -r build-android.zip build-android.apk'
                    }
                }

                if (args.DEPLOYMENT_BRANCHES.contains("${env.BRANCH_NAME}")) {
                    if (args.DEPLOY_TO_STEAM == '1') {
                        stage('Deploy to: Steam') {
                            callUnity "steam-buildfile '${builds}' '${reports}' ${args.STEAM_ID} ${args.STEAM_DEPOTS} ${args.STEAM_BRANCH} 1>'build.vdf'"
                            withCredentials([usernamePassword(credentialsId: args.STEAM_CREDENTIALS, usernameVariable: 'STEAM_CREDS_USR', passwordVariable: 'STEAM_CREDS_PSW')]) {
                                sh 'steamcmd +login $STEAM_CREDS_USR $STEAM_CREDS_PSW +run_app_build "build.vdf" +quit'
                            }
                        }
                    }

                    if (args.DEPLOY_TO_ITCH == '1') {
                        stage('Deploy to: itch.io') {
                            withCredentials([string(credentialsId: args.ITCH_CREDENTIALS, variable: 'BUTLER_API_KEY')]) {
                                if (args.BUILD_FOR_WINDOWS == '1') {
                                    sh 'butler push --if-changed build-windows $ITCH_ID:windows-x64'
                                }
                                if (args.BUILD_FOR_LINUX == '1') {
                                    sh 'butler push --if-changed build-linux $ITCH_ID:linux-x64'
                                }
                                if (args.BUILD_FOR_MAC == '1') {
                                    sh 'butler push --if-changed build-mac $ITCH_ID:mac-x64'
                                }
                                if (args.BUILD_FOR_WEBGL == '1') {
                                    sh 'butler push --if-changed build-webgl $ITCH_ID:html'
                                }
                                if (args.BUILD_FOR_ANDROID == '1') {
                                    sh 'butler push --if-changed build-android.apk $ITCH_ID:android'
                                }
                            }
                        }
                    }
                }
            }
        }
    } catch (err) {
        currentBuild.result = "FAILURE"
        throw err
    } finally {
        junit(testResults: 'reports/*.xml', allowEmptyResults: true)
        dir('reports') {
            deleteDir()
        }
		
		archiveArtifacts(artifacts: 'builds/*.zip', allowEmptyArchive: true)
        dir('builds') {
            deleteDir()
        }
    }
}
