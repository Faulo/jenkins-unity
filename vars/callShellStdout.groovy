def call(String script, Boolean echoScript = false) {
    if (echoScript) {
        echo "> ${script}"
    }

    if (isWindows()) {
        if (!echoScript) {
            echo "+ ${script}"
        }

        // https://stackoverflow.com/questions/2095088/error-when-calling-3rd-party-executable-from-powershell-when-using-an-ide
        return powershell(returnStdout: true, encoding: 'UTF-8', label: 'powershell -- ' + script, script: script + ' 2>$null').trim()
    } else {
        return sh(script: script + ' || true', encoding: 'UTF-8', label: 'sh', returnStdout: true).trim()
    }
}