def String call(String script) {
	if (isUnix()) {
		return sh(script: "$COMPOSE_UNITY ${body}", returnStdout: true).trim();
	} else {
		return bat(script: "%COMPOSE_UNITY% ${body}", returnStdout: true).trim();
	}
}
