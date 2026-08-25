import net.slothsoft.jenkins.unity.InstalledUnityPackage
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

void call(InstalledUnityPackage installedPackage) {
    requireInstalledPackage(installedPackage)
    def options = installedPackage.preparedPackage.options
    if (options.testFormatting) {
        call(installedPackage, 'solution')
    }
    if (options.buildDocumentation) {
        call(installedPackage, 'documentation')
    }
}

void call(InstalledUnityPackage installedPackage, String operation) {
    requireInstalledPackage(installedPackage)
    try {
        switch (operation) {
            case 'solution':
                buildSolution(installedPackage)
                break
            case 'documentation':
                buildDocumentation(installedPackage)
                break
            default:
                throw new IllegalArgumentException("Unknown Unity project build operation '${operation}'")
        }
    } catch (FlowInterruptedException e) {
        currentBuild.result = e.result
        throw e
    }
}

private void buildSolution(InstalledUnityPackage installedPackage) {
    withUnityPackageEnvironment(installedPackage.preparedPackage) {
        dir(installedPackage.reportsDirectory) {
            callUnity "unity-method '${installedPackage.projectDirectory}' Slothsoft.UnityExtensions.Editor.Build.Solution", 'build-solution.xml'
            junit(testResults: 'build-solution.xml')
        }
    }
}

private void buildDocumentation(InstalledUnityPackage installedPackage) {
    catchError(stageResult: 'FAILURE', buildResult: 'UNSTABLE', catchInterruptions: false) {
        withUnityPackageEnvironment(installedPackage.preparedPackage) {
            dir("${installedPackage.projectDirectory}/.Documentation") {
                deleteDir()
                callUnity "unity-documentation '${installedPackage.projectDirectory}'"
                callDocFX(installedPackage.preparedPackage.context.packageId)
            }
        }
    }
}

private void requireInstalledPackage(InstalledUnityPackage installedPackage) {
    if (!installedPackage) {
        throw new IllegalArgumentException('installedPackage must not be null')
    }
}
