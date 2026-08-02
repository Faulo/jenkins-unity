import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def call(Closure body) {
    call(env.JENKINS_UNITY_CONTAINER, body)
}

def call(String containerName, Closure body) {
    if (!containerName || !(containerName ==~ /[A-Za-z0-9][A-Za-z0-9_.-]*/)) {
        error "Invalid Unity container name '${containerName}'."
    }

    def inspection
    try {
        inspection = inspectContainer(containerName)
    } catch (FlowInterruptedException e) {
        throw e
    } catch (Throwable e) {
        error "Unity container '${containerName}' is absent or inaccessible: ${e.message}"
    }

    def inspectionParts = inspection.split('\\|', -1)
    if (inspectionParts.size() != 3) {
        error "Docker returned invalid inspection data for Unity container '${containerName}'."
    }

    def containerId = inspectionParts[0]
    def isRunning = inspectionParts[1] == 'true'
    def containerOs = inspectionParts[2].toLowerCase()

    if (!isRunning) {
        error "Unity container '${containerName}' is stopped."
    }
    if (!(containerOs in ['linux', 'windows'])) {
        error "Unity container '${containerName}' uses unsupported OS '${containerOs}'."
    }

    validateContainerPath(containerName, containerId, containerOs, pwd())
    if (env.WORKSPACE_TMP) {
        validateContainerPath(containerName, containerId, containerOs, env.WORKSPACE_TMP)
    }

    withEnv([
        "JENKINS_UNITY_CONTAINER=${containerName}",
        "JENKINS_UNITY_CONTAINER_ID=${containerId}",
        "JENKINS_UNITY_CONTAINER_OS=${containerOs}"
    ]) {
        body()
    }
}

Boolean isActive() {
    return env.JENKINS_UNITY_CONTAINER_ID && env.JENKINS_UNITY_CONTAINER_OS
}

def executeShell(String script, Boolean echoScript, String resultMode) {
    if (!isActive()) {
        error 'Unity sidecar execution requested outside withUnity.'
    }
    if (!(resultMode in ['stream', 'stdout', 'status'])) {
        error "Unsupported Unity shell result mode '${resultMode}'."
    }

    def containerId = env.JENKINS_UNITY_CONTAINER_ID
    def containerOs = env.JENKINS_UNITY_CONTAINER_OS
    def currentDirectory = pwd()
    def temporaryDirectory = pwd(tmp: true)
    def token = UUID.randomUUID().toString()
    def extension = containerOs == 'windows' ? 'ps1' : 'sh'
    def scriptName = "with-unity-${token}.${extension}"
    def scriptFile = "${temporaryDirectory}/${scriptName}"
    def markerFile = "${scriptFile}.pid"
    def wrappedScript = containerOs == 'windows'
        ? wrapPowerShell(script, markerFile, resultMode)
        : wrapPosixShell(script, markerFile, resultMode)

    if (echoScript) {
        echo "> ${script}"
    } else {
        echo "+ ${script}"
    }

    try {
        dir(temporaryDirectory) {
            writeFile(file: scriptName, text: wrappedScript, encoding: 'UTF-8')
        }

        def dockerCommand = buildDockerCommand(containerId, containerOs, currentDirectory, scriptFile)
        return runAgentCommand(dockerCommand, resultMode)
    } catch (FlowInterruptedException e) {
        stopSidecarProcess(containerId, containerOs, markerFile)
        throw e
    } catch (Throwable e) {
        stopSidecarProcess(containerId, containerOs, markerFile)
        throw e
    } finally {
        deleteAgentFiles(scriptFile, markerFile)
    }
}

private String inspectContainer(String containerName) {
    def command = "docker container inspect --format '{{.Id}}|{{.State.Running}}|{{.Platform}}' ${quoteForAgent(containerName)}"
    return runAgentStdout(command, "docker inspect -- ${containerName}")
}

private void validateContainerPath(String containerName, String containerId, String containerOs, String path) {
    def command
    if (containerOs == 'windows') {
        def testScript = "if (-not (Test-Path -LiteralPath ${quotePowerShell(path)} -PathType Container)) { exit 1 }"
        command = "docker exec ${containerId} powershell.exe -NoProfile -NonInteractive -Command ${quoteForAgent(testScript)}"
    } else {
        def testScript = 'test -d "$1"'
        command = "docker exec ${containerId} /bin/sh -c ${quoteForAgent(testScript)} sh ${quoteForAgent(path)}"
    }

    if (runAgentStatus(command, "docker path check -- ${containerName}") != 0) {
        error "Unity container '${containerName}' cannot access Jenkins path '${path}' at the identical location."
    }
}

private String buildDockerCommand(String containerId, String containerOs, String currentDirectory, String scriptFile) {
    def environmentArguments = environmentNames().collect { name ->
        "--env ${quoteForAgent(name)}"
    }.join(' ')
    def targetCommand = containerOs == 'windows'
        ? "powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File ${quoteForAgent(scriptFile)}"
        : "setsid --wait /bin/sh ${quoteForAgent(scriptFile)}"

    return "docker exec --workdir ${quoteForAgent(currentDirectory)} ${environmentArguments} ${containerId} ${targetCommand}"
}

private List<String> environmentNames() {
    def names = (env.JENKINS_UNITY_ENV ?: '').split(':', -1).findAll { it }
    def invalidName = names.find { name ->
        !(name ==~ /[A-Za-z_][A-Za-z0-9_]*/)
    }
    if (invalidName) {
        error "Invalid environment variable name '${invalidName}' in JENKINS_UNITY_ENV."
    }

    return names.unique()
}

private String wrapPosixShell(String script, String markerFile, String resultMode) {
    def errorMode = resultMode == 'stdout' ? '' : 'set -e'
    def result = "#!/bin/sh\n" +
        "marker=${quotePosix(markerFile)}\n" +
        "printf '%s\\n' \"\$\$\" > \"\$marker\"\n" +
        "trap 'rm -f -- \"\$marker\"' EXIT\n" +
        (errorMode ? "${errorMode}\n" : '') +
        "${script}\n"

    if (resultMode == 'stdout') {
        result += 'exit 0\n'
    }
    return result
}

private String wrapPowerShell(String script, String markerFile, String resultMode) {
    def result = "\$jenkinsUnityMarker = ${quotePowerShell(markerFile)}\n" +
        "Set-Content -LiteralPath \$jenkinsUnityMarker -Value \$PID -NoNewline\n" +
        "try {\n${script}\n" +
        "    \$jenkinsUnityExitCode = \$LASTEXITCODE\n" +
        "} finally {\n" +
        "    Remove-Item -LiteralPath \$jenkinsUnityMarker -Force -ErrorAction SilentlyContinue\n" +
        "}\n"

    if (resultMode == 'stdout') {
        result += 'exit 0\n'
    } else {
        result += 'if ($jenkinsUnityExitCode) { exit $jenkinsUnityExitCode }\n'
    }
    return result
}

private def runAgentCommand(String command, String resultMode) {
    if (isWindows()) {
        if (resultMode == 'stdout') {
            return powershell(
                returnStdout: true,
                encoding: 'UTF-8',
                label: 'docker exec -- capture',
                script: "${command}\nexit 0"
            ).trim()
        }
        if (resultMode == 'status') {
            return powershell(
                returnStatus: true,
                encoding: 'UTF-8',
                label: 'docker exec -- status',
                script: "${command}\nexit \$LASTEXITCODE"
            ) as int
        }

        powershell(
            encoding: 'UTF-8',
            label: 'docker exec',
            script: "${command}\nif (\$LASTEXITCODE -ne 0) { exit \$LASTEXITCODE }"
        )
        return null
    }

    if (resultMode == 'stdout') {
        return sh(
            script: "${command} || true",
            encoding: 'UTF-8',
            label: 'docker exec -- capture',
            returnStdout: true
        ).trim()
    }
    if (resultMode == 'status') {
        return sh(
            script: command,
            encoding: 'UTF-8',
            label: 'docker exec -- status',
            returnStatus: true
        ) as int
    }

    sh(script: command, encoding: 'UTF-8', label: 'docker exec')
    return null
}

private String runAgentStdout(String command, String label) {
    if (isWindows()) {
        return powershell(
            returnStdout: true,
            encoding: 'UTF-8',
            label: label,
            script: "${command}\nif (\$LASTEXITCODE -ne 0) { exit \$LASTEXITCODE }"
        ).trim()
    }

    return sh(
        returnStdout: true,
        encoding: 'UTF-8',
        label: label,
        script: command
    ).trim()
}

private int runAgentStatus(String command, String label) {
    if (isWindows()) {
        return powershell(
            returnStatus: true,
            encoding: 'UTF-8',
            label: label,
            script: "${command}\nexit \$LASTEXITCODE"
        ) as int
    }

    return sh(
        returnStatus: true,
        encoding: 'UTF-8',
        label: label,
        script: command
    ) as int
}

private void stopSidecarProcess(String containerId, String containerOs, String markerFile) {
    def command
    if (containerOs == 'windows') {
        def stopScript = "if (Test-Path -LiteralPath ${quotePowerShell(markerFile)}) { " +
            "\$processId = Get-Content -LiteralPath ${quotePowerShell(markerFile)}; " +
            'taskkill.exe /PID $processId /T /F | Out-Null }'
        command = "docker exec ${containerId} powershell.exe -NoProfile -NonInteractive -Command ${quoteForAgent(stopScript)}"
    } else {
        def stopScript = '''marker=$1
if [ -f "$marker" ]; then
    pid=$(cat "$marker")
    case "$pid" in
        ''|*[!0-9]*) exit 0 ;;
    esac
    pkill -TERM -g "$pid" 2>/dev/null || true
    count=0
    while pkill -0 -g "$pid" 2>/dev/null && [ "$count" -lt 5 ]; do
        sleep 1
        count=$((count + 1))
    done
    pkill -KILL -g "$pid" 2>/dev/null || true
fi'''
        command = "docker exec ${containerId} /bin/sh -c ${quoteForAgent(stopScript)} sh ${quoteForAgent(markerFile)}"
    }

    def status = runAgentStatus(command, 'docker exec -- stop interrupted process')
    if (status != 0) {
        echo "Unable to confirm cleanup of interrupted process in Unity container '${env.JENKINS_UNITY_CONTAINER}'."
    }
}

private void deleteAgentFiles(String scriptFile, String markerFile) {
    if (isWindows()) {
        def command = "Remove-Item -LiteralPath ${quotePowerShell(scriptFile)}, ${quotePowerShell(markerFile)} -Force -ErrorAction SilentlyContinue"
        powershell(returnStatus: true, encoding: 'UTF-8', label: 'cleanup Unity command', script: command)
    } else {
        def command = "rm -f -- ${quotePosix(scriptFile)} ${quotePosix(markerFile)}"
        sh(returnStatus: true, encoding: 'UTF-8', label: 'cleanup Unity command', script: command)
    }
}

private String quoteForAgent(String value) {
    return isWindows() ? quotePowerShell(value) : quotePosix(value)
}

private String quotePosix(String value) {
    return "'${value.replace("'", "'\"'\"'")}'"
}

private String quotePowerShell(String value) {
    return "'${value.replace("'", "''")}'"
}
