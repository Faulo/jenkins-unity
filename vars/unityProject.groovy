def call(body) {
    node {
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
        ]
        
        body.resolveStrategy = Closure.DELEGATE_FIRST
        body.delegate = args
        body()
        
        def project = "$WORKSPACE/${args.PROJECT_LOCATION}"
        
        
        stage('Folder (re)creation') {
            sh 'rm -rf reports builds logs'
            sh 'mkdir -p reports builds logs'
        }
        
        if (args.PROJECT_AUTOVERSION != "") {
            stage("Versioning") {
                def version = callUnity "autoversion '${args.PROJECT_AUTOVERSION}' '$WORKSPACE'"
                callUnity "unity-project-version '${project}' set '${version}'"
            }
        }
        
        if (args.TEST_MODES != "") {
            stage("Testing") {
                callUnity "unity-tests '${project}' ${args.TEST_MODES} 1>'reports/tests.xml'"
                junit 'reports/tests.xml'
            }
        }
        
        if (args.BUILD_FOR_WINDOWS == '1') {
            stage('Build for: Windows') {
                callUnity "unity-build '$PROJECT' 'builds/build-windows' windows 1>'reports/build-windows.xml'"
                junit 'reports/build-windwos.xml'
                sh 'zip -r build-windows.zip build-windows'                          
                archiveArtifacts artifacts: 'builds/build-windows.zip'
            }
        }
        
        if (args.BUILD_FOR_LINUX == '1') {
            stage('Build for: Linux') {
                callUnity "unity-build '$PROJECT' 'builds/build-linux' linux 1>'reports/build-linux.xml'"
                junit 'reports/build-linux.xml'
                sh 'zip -r build-linux.zip build-linux'                     
                archiveArtifacts artifacts: 'builds/build-linux.zip'                
            }
        }
    }
}