def call(String reportName) {
	if (isUnix()) {
		// docker overlay magic prevents updating dotnet tools, so we have to assume it's there.
		callShell "dotnet tool restore"
		callShell "dotnet tool run docfx"
	} else {
		def isInstalled = callShellStdout("dotnet tool list -g").contains("docfx")
		if (!isInstalled) {
			callShell "dotnet tool install -g docfx"
		}

		callShell "docfx"
	}

	publishHTML([
		allowMissing: false,
		alwaysLinkToLastBuild: false,
		keepAll: false,
		reportDir: 'html',
		reportFiles: 'index.html',
		reportName: reportName,
		reportTitles: '',
		useWrapperFileDirectly: true
	])
}