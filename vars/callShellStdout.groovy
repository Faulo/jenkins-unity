def call(String script, Boolean echoScript = false) {
	if (echoScript) {
		echo "> ${script}";
	}

	if (isWindows()) {
		// https://stackoverflow.com/questions/2095088/error-when-calling-3rd-party-executable-from-powershell-when-using-an-ide
		return powershell(returnStdout: true, encoding: 'UTF-8', label: 'powershell: ' + script, script: '''
	        $ErrorActionPreference = 'Continue'
	        $WarningPreference = 'Continue'
	        $VerbosePreference = 'Continue'
	        $DebugPreference = 'Continue'
	        $InformationPreference = 'Continue'

	    ''' + script + ' 2>$null').trim();
	} else {
		return sh(script: script + ' || true', encoding: 'UTF-8', label: 'sh: ' + script, returnStdout: true).trim();
	}
}