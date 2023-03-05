def int call(String script) {
	if (isUnix()) {
		return sh(script: script, returnStatus: true) as int;
	} else {
		return powershell(script: script, returnStatus: true) as int;
	}
}