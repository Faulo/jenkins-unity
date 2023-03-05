def void call(String script) {
	if (isUnix()) {
		sh(script: script)
	} else {
		powershell(script: script)
	}
}