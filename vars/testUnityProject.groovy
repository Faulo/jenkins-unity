import net.slothsoft.jenkins.unity.InstalledUnityPackage
import net.slothsoft.jenkins.unity.UnityProjectContext
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

void call(UnityProjectContext project) {
    requireProject(project)
    if (project.options.testFormatting) {
        call(project, 'formatting')
    }
    if (project.options.testUnity) {
        call(project, 'unity')
    }
}

void call(UnityProjectContext project, String operation) {
    requireProject(project)
    try {
        switch (operation) {
            case 'formatting':
                testFormatting(project)
                break
            case 'unity':
                testUnity(project)
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

private void testFormatting(UnityProjectContext project) {
    def options = project.options
    dir(project.workspaceDirectory) {
        if (!fileExists(options.formattingLocation)) {
            unstable "Formatting configuration '${options.formattingLocation}' is missing."
            return
        }
        writeFile(file: "${project.projectDirectory}/.editorconfig", text: readFile(options.formattingLocation))
    }

    def solutions = dir(project.projectDirectory) {
        findFiles(glob: '*.sln').collect { file -> file.path.toString() }
    }
    if (!solutions) {
        error "No solution exists in '${project.projectDirectory}'."
    }

    withUnityProjectEnvironment(options) {
        for (String solution : solutions) {
            callDotnetFormat("${project.projectDirectory}/${solution}", project.reportsDirectory, options.formattingExclusions.join(' '))
        }
    }
}

private void testUnity(UnityProjectContext project) {
    withUnityProjectEnvironment(project.options) {
        dir(project.reportsDirectory) {
            callUnity "unity-tests '${project.projectDirectory}' ${project.options.unityTestModes.join(' ')}", 'tests.xml'
            junit(testResults: 'tests.xml', allowEmptyResults: true)
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
