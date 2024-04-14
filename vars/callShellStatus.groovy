def int call(String script) {
	echo "> ${script}";
	if (isUnix()) {
		return sh(script: script, encoding: 'UTF-8', returnStatus: true) as int;
	} else {
		return powershell(script: script, encoding: 'UTF-8', returnStatus: true) as int;
	}
}