def String call(String script) {
	if (isUnix()) {
		return sh(script: script, encoding: 'UTF-8', returnStdout: true).trim();
	} else {
		return powershell(script: script, encoding: 'UTF-8', returnStdout: true).trim();
	}
}