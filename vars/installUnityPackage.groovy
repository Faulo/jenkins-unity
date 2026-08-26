import net.slothsoft.jenkins.unity.InstalledUnityPackage
import net.slothsoft.jenkins.unity.PreparedUnityPackage
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

InstalledUnityPackage call(PreparedUnityPackage preparedPackage) {
    if (!preparedPackage) {
        throw new IllegalArgumentException('preparedPackage must not be null')
    }

    def executionId = preparedPackage.executionId.replace('-', '').take(8)
    def invocationId = UUID.randomUUID().toString().replace('-', '').take(12)
    def workDirectory = "${pwd(tmp: true)}/unity-pkg-${executionId}-${invocationId}"
    def packageDirectory = "${workDirectory}/package"
    def projectDirectory = "${workDirectory}/project"
    def reportsDirectory = "${workDirectory}/reports"
    try {
        dir(workDirectory) {
            deleteDir()
            dir('package') {
                unstash preparedPackage.packageStash
            }
        }

        withUnityPackageEnvironment(preparedPackage, packageDirectory) {
            dir(reportsDirectory) {
                callUnity "unity-package-install '${packageDirectory}' '${projectDirectory}'", 'package-install.xml'
                junit(testResults: 'package-install.xml')
            }
        }

        if (preparedPackage.options.testFormatting) {
            dir(projectDirectory) {
                unstash preparedPackage.configurationStash
                if (!fileExists(preparedPackage.options.formattingLocation)) {
                    error "Formatting configuration '${preparedPackage.options.formattingLocation}' does not exist in the prepared source."
                }
                if (preparedPackage.options.formattingLocation != '.editorconfig') {
                    writeFile(file: '.editorconfig', text: readFile(preparedPackage.options.formattingLocation))
                }
            }
        }

        new InstalledUnityPackage(preparedPackage, workDirectory, packageDirectory, projectDirectory, reportsDirectory)
    } catch (FlowInterruptedException e) {
        currentBuild.result = e.result
        deleteInstallation(workDirectory)
        throw e
    } catch (Throwable e) {
        deleteInstallation(workDirectory)
        throw e
    }
}

private void deleteInstallation(String workDirectory) {
    dir(workDirectory) {
        deleteDir()
    }
}
