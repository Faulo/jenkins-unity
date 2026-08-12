def call(String script, Boolean echoScript = false) {
    if (withUnity.isActive()) {
        return withUnity.executeShell(script, echoScript, 'stream')
    }

    if (echoScript) {
        echo "> ${script}"
    }

    if (isWindows()) {
        if (!echoScript) {
            echo "+ ${script}"
        }

        pwsh(encoding: 'UTF-8', label: 'pwsh -- ' + script, script: script)
    } else {
        sh(script: script, encoding: 'UTF-8', label: 'sh')
    }
}
