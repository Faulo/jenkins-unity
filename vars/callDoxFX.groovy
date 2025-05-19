def String call() {
	if (isUnix()) {
		// docker overlay magic prevents updating docfx, so we have to assume it's there.
		callShell "dotnet tool restore"
		callShell "dotnet tool run docfx"
	} else {
		def isInstalled = callShellStdout("dotnet tool list -g").contains("docfx")
		if (isInstalled) {
			callShell "dotnet tool update -g docfx"
		} else {
			callShell "dotnet tool install -g docfx"
		}

		callShell "docfx"
	}
}