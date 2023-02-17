def String call(String body) {
    return sh(script: "\$COMPOSE_UNITY ${body}", returnStdout: true).trim();
}