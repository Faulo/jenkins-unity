def assertValue(actual, expected, description) {
    if (actual != expected) {
        error "${description}: expected '${expected}', got '${actual}'"
    }
}

properties([
    parameters([
        choice(name: 'DOCKER_NAMESPACE', choices: ['faulo', 'tmp'], description: 'Selects the compose-unity integration image namespace.')
    ])
])

if (params.DOCKER_NAMESPACE == 'tmp') {
    env.JENKINS_UNITY_CONTAINER = 'tmp_compose-unity'
}

def candidateImage = params.DOCKER_NAMESPACE == 'tmp'
def integrationNode = candidateImage ? 'Garl || Dende' : 'compose-unity'
def packageUnityAgents = candidateImage
    ? [Windows: 'Dende', Linux: 'Garl']
    : [Windows: 'windows && compose-unity', Linux: 'linux && compose-unity']
def projectUnityAgent = candidateImage ? 'Garl' : 'compose-unity'
node(integrationNode) {
    stage('Pipeline Steps 0.5.0') {
        assertValue(isWindows(), !isUnix(), 'isWindows is expected to invert isUnix')

        def nodeName = env.NODE_NAME
        def workspace = pwd()
        nodeIfCurrentDoesNotMatch(nodeName) {
            assertValue(env.NODE_NAME, nodeName, 'nodeIfCurrentDoesNotMatch is expected to reuse the current node')
            assertValue(pwd(), workspace, 'nodeIfCurrentDoesNotMatch is expected to reuse the current workspace')
        }

        dir("${env.WORKSPACE_TMP}/jenkins-unity-integration") {
            deleteDir()
            writeFile(file: 'pipeline-steps.env', text: 'JENKINS_UNITY_PIPELINE_STEPS=0.5.0')
            withEnvFile('pipeline-steps.env') {
                assertValue(env.JENKINS_UNITY_PIPELINE_STEPS, '0.5.0', 'withEnvFile is expected to apply plugin-owned environment values')
            }
            deleteDir()
        }
    }

    stage('withUnity') {
        def outsideStatus = callShellStatus 'compose-unity exec unity-help'
        assertValue(outsideStatus == 0, false, 'compose-unity is expected to fail outside withUnity')

        withUnity {
            assertValue(env.PIPELINE_DOCKER_CONTAINER_ID ? true : false, true, 'withUnity is expected to expose the plugin container identity')

            def insideStatus = callShellStatus 'compose-unity exec unity-help'
            assertValue(insideStatus == 0, true, 'compose-unity is expected to pass inside withUnity')
        }
    }

    stage('Runtime credential files') {
        dir("${env.WORKSPACE_TMP}/jenkins-unity-credential-files") {
            deleteDir()
            writeFile(file: 'unity-user.txt', text: 'integration-user')
            writeFile(file: 'unity-password.txt', text: 'integration-password')
            writeFile(file: 'empty.txt', text: '')

            def credentialRoot = pwd()
            def unsetDirectCredentials = isWindows()
                ? 'Remove-Item Env:UNITY_CREDENTIALS_USR, Env:UNITY_CREDENTIALS_PSW -ErrorAction SilentlyContinue; '
                : 'unset UNITY_CREDENTIALS_USR UNITY_CREDENTIALS_PSW; '

            withEnv([
                'JENKINS_UNITY_ENV=UNITY_CREDENTIALS_USR_FILE:UNITY_CREDENTIALS_PSW_FILE',
                "UNITY_CREDENTIALS_USR_FILE=${credentialRoot}/unity-user.txt",
                "UNITY_CREDENTIALS_PSW_FILE=${credentialRoot}/unity-password.txt"
            ]) {
                withUnity {
                    def status = callShellStatus "${unsetDirectCredentials}compose-unity exec unity-help"
                    assertValue(status == 0, true, 'compose-unity is expected to accept a complete credential file pair')
                }
            }

            withEnv([
                'JENKINS_UNITY_ENV=UNITY_CREDENTIALS_USR:UNITY_CREDENTIALS_USR_FILE:UNITY_CREDENTIALS_PSW_FILE',
                'UNITY_CREDENTIALS_USR=integration-user',
                "UNITY_CREDENTIALS_USR_FILE=${credentialRoot}/unity-user.txt",
                "UNITY_CREDENTIALS_PSW_FILE=${credentialRoot}/unity-password.txt"
            ]) {
                withUnity {
                    def status = callShellStatus 'compose-unity exec unity-help'
                    assertValue(status == 0, false, 'compose-unity is expected to reject direct and file-backed forms together')
                }
            }

            withEnv([
                'JENKINS_UNITY_ENV=UNITY_CREDENTIALS_USR_FILE:UNITY_CREDENTIALS_PSW_FILE',
                "UNITY_CREDENTIALS_USR_FILE=${credentialRoot}/missing.txt",
                "UNITY_CREDENTIALS_PSW_FILE=${credentialRoot}/unity-password.txt"
            ]) {
                withUnity {
                    def status = callShellStatus "${unsetDirectCredentials}compose-unity exec unity-help"
                    assertValue(status == 0, false, 'compose-unity is expected to reject a missing credential file')
                }
            }

            withEnv([
                'JENKINS_UNITY_ENV=UNITY_CREDENTIALS_USR_FILE:UNITY_CREDENTIALS_PSW_FILE',
                "UNITY_CREDENTIALS_USR_FILE=${credentialRoot}/empty.txt",
                "UNITY_CREDENTIALS_PSW_FILE=${credentialRoot}/unity-password.txt"
            ]) {
                withUnity {
                    def status = callShellStatus "${unsetDirectCredentials}compose-unity exec unity-help"
                    assertValue(status == 0, false, 'compose-unity is expected to reject an empty credential file')
                }
            }

            withEnv([
                'JENKINS_UNITY_ENV=UNITY_CREDENTIALS_USR_FILE',
                "UNITY_CREDENTIALS_USR_FILE=${credentialRoot}/unity-user.txt"
            ]) {
                withUnity {
                    def status = callShellStatus "${unsetDirectCredentials}compose-unity exec unity-help"
                    assertValue(status == 0, false, 'compose-unity is expected to reject an incomplete credential pair')
                }
            }

            deleteDir()
        }
    }
}

unityPackagePipeline {
    PACKAGE_LOCATION = '.jenkins/fixtures/unity-package'
    PACKAGE_ID = 'net.slothsoft.jenkins-unity.integration'
    UNITY_MANIFEST_LOCATION = '.jenkins/manifest.json'
    TEST_CHANGELOG = true
    TEST_FORMATTING = true
    FORMATTING_LOCATION = '.jenkins/fixtures/unity-package/.editorconfig'
    TEST_UNITY = true
    UNITY_TEST_MODES = ['EditMode']
    UNITY_AGENTS = packageUnityAgents
    BUILD_DOCUMENTATION = true
    PUBLISH_TO_VERDACCIO = false

}

unityProjectPipeline {
    UNITY_AGENT = projectUnityAgent
    PROJECT_LOCATION = '.jenkins/fixtures/unity-project'
    PROJECT_ID = 'Jenkins Unity Project Integration'
    PROJECT_BRANCH = 'main'
    TEST_FORMATTING = true
    FORMATTING_LOCATION = '.jenkins/fixtures/unity-project/.editorconfig'
    TEST_UNITY = true
    UNITY_TEST_MODES = ['EditMode']
    BUILD_DOCUMENTATION = true
    DEPLOY_TO_STEAM = false
    DEPLOY_TO_ITCH = false
}
