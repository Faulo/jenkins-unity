def String call(String solutionFile, String reportsDirectory, String exclude = '') {
	if (isUnix()) {
		// docker overlay magic prevents updating dotnet tools, so we have to assume it's there.
		callShell "dotnet tool restore"
	} else {
		def isInstalled = callShellStdout("dotnet tool list -g").contains("dotnet-format")
		if (isInstalled) {
			callShell "dotnet tool update -g dotnet-format"
		} else {
			callShell "dotnet tool install -g dotnet-format"
		}
	}

	dir(reportsDirectory) {
		if (exclude != '') {
			exclude = " --exclude ${exclude}"
		}

		callShellStatus "dotnet format '${solutionFile}' --verify-no-changes --verbosity normal --report '${reportsDirectory}'${exclude}"

		def jsonFile = "${reportsDirectory}/format-report.json";
		def xmlFile = "${reportsDirectory}/format-report.xml";

		if (!fileExists(jsonFile)) {
			error "dotnet format failed to create '${jsonFile}'."
		}

		callUnity "transform-dotnet-format '${jsonFile}'", xmlFile;
		if (!fileExists(xmlFile)) {
			error "transform-dotnet-format failed to create '${xmlFile}'."
		}

		junit(testResults: 'format-report.xml', allowEmptyResults: true)
	}
}