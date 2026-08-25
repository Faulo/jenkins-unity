import net.slothsoft.jenkins.unity.InstalledUnityPackage
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

void call(InstalledUnityPackage installedPackage) {
    requireInstalledPackage(installedPackage)
    def options = installedPackage.preparedPackage.options
    if (options.testFormatting) {
        call(installedPackage, 'formatting')
    }
    if (options.testUnity) {
        call(installedPackage, 'unity')
    }
}

void call(InstalledUnityPackage installedPackage, String operation) {
    requireInstalledPackage(installedPackage)
    try {
        switch (operation) {
            case 'formatting':
                testFormatting(installedPackage)
                break
            case 'unity':
                testUnity(installedPackage)
                break
            default:
                throw new IllegalArgumentException("Unknown Unity project test operation '${operation}'")
        }
    } catch (FlowInterruptedException e) {
        currentBuild.result = e.result
        throw e
    }
}

private void testFormatting(InstalledUnityPackage installedPackage) {
    def exclusions = installedPackage.preparedPackage.options.formattingExclusions.join(' ')
    withUnityPackageEnvironment(installedPackage.preparedPackage) {
        callDotnetFormat("${installedPackage.projectDirectory}/project.sln", installedPackage.reportsDirectory, exclusions)
    }
}

private void testUnity(InstalledUnityPackage installedPackage) {
    withUnityPackageEnvironment(installedPackage.preparedPackage) {
        dir(installedPackage.reportsDirectory) {
            def testModes = installedPackage.preparedPackage.options.unityTestModes.join(' ')
            callUnity "unity-tests '${installedPackage.projectDirectory}' ${testModes}", 'tests.xml'
            junit(testResults: 'tests.xml', allowEmptyResults: true)
        }
    }
}

private void requireInstalledPackage(InstalledUnityPackage installedPackage) {
    if (!installedPackage) {
        throw new IllegalArgumentException('installedPackage must not be null')
    }
}
