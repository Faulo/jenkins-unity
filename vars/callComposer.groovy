def call(String body) {
	if (!env.COMPOSE_UNITY) {
		env.COMPOSE_UNITY = "compose-unity"
	}
	return callShellStdout("$COMPOSE_UNITY ${body}");
}