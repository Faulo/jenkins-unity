def void call(String script) {
	echo "> ${script}";
	if (isUnix()) {
		sh(script: script, encoding: 'UTF-8');
	} else {
		// https://stackoverflow.com/questions/2095088/error-when-calling-3rd-party-executable-from-powershell-when-using-an-ide
		powershell(script: "${script} 2>&1 | %{ \"\$_\" }", encoding: 'UTF-8');
	}
}