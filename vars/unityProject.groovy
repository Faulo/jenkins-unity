def call(body) {
    def args= [
        PROJECT_LOCATION : "",
        PROJECT_AUTOVERSION : "",
        
        REPORTS : "${env.WORKSPACE}/reports",
        BUILDS : "${env.WORKSPACE}/builds",
        LOGS : "${env.WORKSPACE}/logs",
        
        TEST_MODES : "",
        
        BUILD_FOR_WINDOWS : "0",
        BUILD_FOR_LINUX : "0",
        BUILD_FOR_MAC : "0",
        BUILD_FOR_WEBGL : "0",
        BUILD_FOR_ANDROID : "0",
        
        DEPLOY_TO_STEAM : "0",
        STEAM_ID : "",
        STEAM_DEPOTS : "",
        STEAM_BRANCH : env.BRANCH_NAME.replace("\\", "-"),
        
        DEPLOY_TO_ITCH = "${args.DEPLOY_TO_ITCH}",        
        ITCH_ID = "",
    ]
    
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = args
    body()
    
    if (args.PROJECT_AUTOVERSION != "") {
        stage("Versioning") {
            def version = sh(script: "$COMPOSE_UNITY autoversion '${args.PROJECT_AUTOVERSION}' '$WORKSPACE'", returnStdout: true).trim()
            sh "$COMPOSE_UNITY unity-project-version '${args.PROJECT}' set '${version}'"
        }
    }
    
    if (args.TEST_MODES != "") {
        stage("Testing") {
            sh "$COMPOSE_UNITY unity-tests '${args.PROJECT}' ${args.TEST_MODES} 1>'$REPORTS/tests.xml'"
            junit 'reports/tests.xml'
        }
    }
}