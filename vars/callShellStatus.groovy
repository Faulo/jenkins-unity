def call(String script, Boolean echoScript = false) {
    if (withUnity.isActive()) {
        return withUnity.executeShell(script, echoScript, 'status')
    }

    if (echoScript) {
        echo "> ${script}"
    }

    if (isWindows()) {
        if (!echoScript) {
            echo "+ ${script}"
        }

        return pwsh(returnStatus: true, encoding: 'UTF-8', label: 'pwsh -- ' + script, script: script) as int
    } else {
        return sh(script: script, encoding: 'UTF-8', label: 'sh', returnStatus: true) as int
    }
}
