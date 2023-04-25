def String call(String body) {
	return callShellStdout("$COMPOSE_UNITY ${body}");
}