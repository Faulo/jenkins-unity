def assertValue(actual, expected, description) {
    if (actual != expected) {
        error "${description}: expected '${expected}', got '${actual}'"
    }
}

node('compose-unity') {
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
