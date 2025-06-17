def call(String solutionFile, String reportsDirectory, String exclude = '') {
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