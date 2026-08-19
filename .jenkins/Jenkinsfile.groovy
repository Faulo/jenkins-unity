def assertValue(actual, expected, description) {
    if (actual != expected) {
        error "${description}: expected '${expected}', got '${actual}'"
    }
}

node('compose-unity') {
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
        assertValue(outsideStatus == 0, false, "compose-unity is expected to fail outside withUnity")

        withUnity {
            assertValue(env.PIPELINE_DOCKER_CONTAINER_ID ? true : false, true, "withUnity is expected to expose the plugin container identity")

            def insideStatus = callShellStatus 'compose-unity exec unity-help'
            assertValue(insideStatus == 0, true, "compose-unity is expected to pass inside withUnity")
        }
    }
}
