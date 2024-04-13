def void call(String script) {
	if (isUnix()) {
		sh(script: script, encoding: 'UTF-8');
	} else {
		bat(script: script, encoding: 'UTF-8');
	}
}