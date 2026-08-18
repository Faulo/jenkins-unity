def call(String body, String file = "") {
    def executionIdentity = env.PIPELINE_DOCKER_CONTAINER_ID ?: env.NODE_NAME
    if (CallUnityInitializer.initialize(executionIdentity)) {
        callComposer('update --no-interaction --no-dev --optimize-autoloader --classmap-authoritative')
    }

    def result = callComposer("exec ${body}")

    if (file != "") {
        writeFile(file: file, text: result, encoding: "UTF-8")
    }

    return result
}
