def String call(String body) {
    def result = sh(script: "\$COMPOSE_UNITY ${body}", returnStdout: true).trim();
    if (sh.returnStatus != 0) {
        throw new Exception("callUnity failed with exit code ${sh.returnStatus}")
    }
    return result
}