def void call(String script) {
	if (isUnix()) {
		sh(script: script)
	} else {
		bat(script: script)
	}
}