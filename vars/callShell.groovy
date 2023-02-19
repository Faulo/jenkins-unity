def String call(String script) {
	return sh(script: script, returnStdout: true).trim();
}