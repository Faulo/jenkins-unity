def call(Closure body) {
    call(env.JENKINS_UNITY_CONTAINER?.toString(), body)
}

def call(String containerName, Closure body) {
    if (!containerName) {
        error "Invalid Unity container name '${containerName}'."
    }

    def environmentNames = (env.JENKINS_UNITY_ENV ?: '').tokenize(':').unique()
    insideDockerContainer(
        container: containerName,
        environment: environmentNames
    ) {
        withEnv([
            "JENKINS_UNITY_CONTAINER=${env.PIPELINE_DOCKER_CONTAINER_NAME}",
            "JENKINS_UNITY_CONTAINER_ID=${env.PIPELINE_DOCKER_CONTAINER_ID}",
            "JENKINS_UNITY_CONTAINER_OS=${env.PIPELINE_DOCKER_CONTAINER_OS}"
        ]) {
            body()
        }
    }
}
