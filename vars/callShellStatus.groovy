def int call(String script) {
	echo "> ${script}";
	if (isUnix()) {
		return sh(script: script, encoding: 'UTF-8', returnStatus: true) as int;
	} else {
		// https://stackoverflow.com/questions/2095088/error-when-calling-3rd-party-executable-from-powershell-when-using-an-ide
		return powershell(returnStatus: true, encoding: 'UTF-8', script: '''
	        $ErrorActionPreference = 'Continue'
	        $WarningPreference = 'Continue'
	        $VerbosePreference = 'Continue'
	        $DebugPreference = 'Continue'
	        $InformationPreference = 'Continue'

	    ''' + script + ' 2>&1') as int;
	}
}