def int call(String script) {
	if (isUnix()) {
		return sh(script: script, returnStatus: true) as int;
	} else {
		return bat(script: script, returnStatus: true) as int;
	}
}