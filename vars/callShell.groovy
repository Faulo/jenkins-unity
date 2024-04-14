def void call(String script) {
	echo "> ${script}";
	if (isUnix()) {
		sh(script: script, encoding: 'UTF-8');
	} else {
		powershell(script: script, encoding: 'UTF-8');
	}
}