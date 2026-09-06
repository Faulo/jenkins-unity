def call(Closure body) {
    def containerName = env.JENKINS_UNITY_CONTAINER?.toString()
    if (!containerName && env.JENKINS_UNITY_CONTAINER_LABEL) {
        containerName = findContainerByLabel(env.JENKINS_UNITY_CONTAINER_LABEL.toString())
    }
    call(containerName, body)
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

private String findContainerByLabel(String containerLabel) {
    def output
    if (isWindows()) {
        output = powershell(
            returnStdout: true,
            encoding: 'UTF-8',
            label: 'find Unity container by label',
            script: '''$containerNames = & docker ps --filter "label=$env:JENKINS_UNITY_CONTAINER_LABEL" --format '{{.Names}}'
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
$containerNames'''
        )
    } else {
        output = sh(
            returnStdout: true,
            encoding: 'UTF-8',
            label: 'find Unity container by label',
            script: '''docker ps --filter "label=$JENKINS_UNITY_CONTAINER_LABEL" --format '{{.Names}}' '''
        )
    }

    def containerName = output.readLines().collect { it.trim() }.find { it }
    if (!containerName) {
        error "No running Unity container matches label '${containerLabel}'."
    }
    return containerName
}
