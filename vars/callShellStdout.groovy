def call(String script, Boolean echoScript = false) {
    if (withUnity.isActive()) {
        return withUnity.executeShell(script, echoScript, 'stdout')
    }

    if (echoScript) {
        echo "> ${script}"
    }

    if (!echoScript && isWindows()) {
        echo "+ ${script}"
    }

    String temporaryDirectory = pwd(tmp: true)
    String outputName = "call-shell-stdout-${UUID.randomUUID()}.txt"
    String outputFile = "${temporaryDirectory}/${outputName}"

    try {
        if (isWindows()) {
            pwsh(
                encoding: 'UTF-8',
                label: 'pwsh -- ' + script,
                script: streamPowerShell(script, outputFile)
            )
        } else {
            sh(
                script: "(${script}\n) | tee ${quotePosix(outputFile)}",
                encoding: 'UTF-8',
                label: 'sh'
            )
        }

        String output = ''
        dir(temporaryDirectory) {
            output = readFile(file: outputName, encoding: 'UTF-8').trim()
        }
        return output
    } finally {
        deleteOutputFile(outputFile)
    }
}

private static String streamPowerShell(String script, String outputFile) {
    return "\$jenkinsUnityOutput = ${quotePowerShell(outputFile)}\n" +
        "\$jenkinsUnityUtf8 = New-Object System.Text.UTF8Encoding(\$false)\n" +
        "\$jenkinsUnityWriter = New-Object System.IO.StreamWriter(\$jenkinsUnityOutput, \$false, \$jenkinsUnityUtf8)\n" +
        "try {\n" +
        "    & {\n${script}\n    } | ForEach-Object {\n" +
        "        \$jenkinsUnityText = \$_ | Out-String -Stream\n" +
        "        foreach (\$jenkinsUnityLine in \$jenkinsUnityText) {\n" +
        "            \$jenkinsUnityWriter.WriteLine(\$jenkinsUnityLine)\n" +
        "            Write-Output \$jenkinsUnityLine\n" +
        "        }\n" +
        "    }\n" +
        "} finally {\n" +
        "    \$jenkinsUnityWriter.Dispose()\n" +
        "}\n"
}

private void deleteOutputFile(String outputFile) {
    if (isWindows()) {
        pwsh(
            returnStatus: true,
            encoding: 'UTF-8',
            label: 'cleanup captured shell output',
            script: "Remove-Item -LiteralPath ${quotePowerShell(outputFile)} -Force -ErrorAction SilentlyContinue"
        )
    } else {
        sh(
            returnStatus: true,
            encoding: 'UTF-8',
            label: 'cleanup captured shell output',
            script: "rm -f -- ${quotePosix(outputFile)}"
        )
    }
}

private static String quotePosix(String value) {
    return "'${value.replace("'", "'\"'\"'")}'"
}

private static String quotePowerShell(String value) {
    return "'${value.replace("'", "''")}'"
}
