import net.slothsoft.jenkins.unity.UnityReports

def call(String solutionFile, String reportsDirectory, String exclude = '') {
    dir(reportsDirectory) {
        if (exclude != '') {
            exclude = " --exclude ${exclude}"
        }

        callShellStatus "dotnet format '${solutionFile}' --verify-no-changes --verbosity normal --report '${reportsDirectory}'${exclude}"

        def jsonFile = "${reportsDirectory}/format-report.json"
        def xmlFile = "${reportsDirectory}/format-report.xml"

        if (!fileExists(jsonFile)) {
            error "dotnet format failed to create '${jsonFile}'."
        }

        UnityReports.run(this, [testResults: 'format-report.xml', allowEmptyResults: true]) {
            callComposer "exec transform-dotnet-format '${jsonFile}'", xmlFile
            if (!fileExists(xmlFile)) {
                error "transform-dotnet-format failed to create '${xmlFile}'."
            }
        }
    }
}