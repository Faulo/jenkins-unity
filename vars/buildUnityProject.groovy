import net.slothsoft.jenkins.unity.InstalledUnityPackage
import net.slothsoft.jenkins.unity.UnityProjectContext
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

void call(UnityProjectContext project) {
    requireProject(project)
    def options = project.options
    if (options.testFormatting || options.buildDocumentation) {
        call(project, 'solution')
    }
    if (options.buildDocumentation) {
        call(project, 'documentation')
    }
    if (options.buildForWindows) {
        call(project, 'windows')
    }
    if (options.buildForLinux) {
        call(project, 'linux')
    }
    if (options.buildForMac) {
        call(project, 'mac')
    }
    if (options.buildForWebGL) {
        call(project, 'webgl')
    }
    if (options.buildForAndroid) {
        call(project, 'android')
    }
}

void call(UnityProjectContext project, String operation) {
    requireProject(project)
    try {
        switch (operation) {
            case 'solution':
                buildSolution(project)
                break
            case 'documentation':
                buildDocumentation(project)
                break
            case 'windows':
            case 'linux':
            case 'mac':
                buildDesktop(project, operation)
                break
            case 'webgl':
                buildWebGL(project)
                break
            case 'android':
                buildAndroid(project)
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

private void buildSolution(UnityProjectContext project) {
    withUnityProjectEnvironment(project.options) {
        dir(project.reportsDirectory) {
            callUnity "unity-method '${project.projectDirectory}' Slothsoft.UnityExtensions.Editor.Build.Solution", 'build-solution.xml'
            junit(testResults: 'build-solution.xml')
        }
    }
}

private void buildDocumentation(UnityProjectContext project) {
    catchError(stageResult: 'FAILURE', buildResult: 'UNSTABLE', catchInterruptions: false) {
        withUnityProjectEnvironment(project.options) {
            dir(project.documentationDirectory) {
                deleteDir()
                callUnity "unity-documentation '${project.projectDirectory}'"
                callDocFX(project.projectId)
            }
        }
    }
}

private void buildDesktop(UnityProjectContext project, String platform) {
    def buildName = "${project.options.buildName}-${platform}"
    withUnityProjectEnvironment(project.options) {
        dir(project.reportsDirectory) {
            callUnity "unity-build '${project.projectDirectory}' '${project.reportsDirectory}/${buildName}' ${platform}", "${buildName}.xml"
            junit(testResults: "${buildName}.xml")
            zip(zipFile: "${buildName}.zip", dir: buildName, archive: true)
        }
    }
}

private void buildWebGL(UnityProjectContext project) {
    def buildName = "${project.options.buildName}-webgl"
    withUnityProjectEnvironment(project.options) {
        dir(project.reportsDirectory) {
            callUnity "unity-module-install '${project.projectDirectory}' webgl", 'install-webgl.xml'
            junit(testResults: 'install-webgl.xml')
            callUnity "unity-method '${project.projectDirectory}' Slothsoft.UnityExtensions.Editor.Build.WebGL -- -buildTarget WebGL '${project.reportsDirectory}/${buildName}'", "${buildName}.xml"
            junit(testResults: "${buildName}.xml")
            zip(zipFile: "${buildName}.zip", dir: buildName, archive: true)
            publishHTML([
                allowMissing: false,
                alwaysLinkToLastBuild: false,
                keepAll: false,
                reportDir: buildName,
                reportFiles: 'index.html',
                reportName: 'WebGL Build',
                reportTitles: '',
                useWrapperFileDirectly: true
            ])
        }
    }
}

private void buildAndroid(UnityProjectContext project) {
    def buildName = "${project.options.buildName}-android"
    withUnityProjectEnvironment(project.options) {
        dir(project.reportsDirectory) {
            callUnity "unity-module-install '${project.projectDirectory}' android", 'install-android.xml'
            junit(testResults: 'install-android.xml')
            callUnity "unity-method '${project.projectDirectory}' Slothsoft.UnityExtensions.Editor.Build.Android -- -buildTarget Android '${project.reportsDirectory}/${buildName}.apk'", "${buildName}.xml"
            junit(testResults: "${buildName}.xml")
            archiveArtifacts(artifacts: "${buildName}.apk")
        }
    }
}

private void requireInstalledPackage(InstalledUnityPackage installedPackage) {
    if (!installedPackage) {
        throw new IllegalArgumentException('installedPackage must not be null')
    }
}

private void requireProject(UnityProjectContext project) {
    if (!project) {
        throw new IllegalArgumentException('project must not be null')
    }
}
