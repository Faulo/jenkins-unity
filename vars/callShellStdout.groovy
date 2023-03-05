def String call(String script) {
	if (isUnix()) {
		return sh(script: script, returnStdout: true).trim();
	} else {
		return powershell(script: script, returnStdout: true).trim();
	}
}