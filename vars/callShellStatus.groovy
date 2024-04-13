def int call(String script) {
	if (isUnix()) {
		return sh(script: script, encoding: 'UTF-8', returnStatus: true) as int;
	} else {
		return bat(script: script, encoding: 'UTF-8', returnStatus: true) as int;
	}
}