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
            powershell(
                encoding: 'UTF-8',
                label: 'powershell -- ' + script,
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
        "[System.IO.File]::WriteAllText(\$jenkinsUnityOutput, '', \$jenkinsUnityUtf8)\n" +
        "& {\n${script}\n} 2>&1 | ForEach-Object {\n" +
        "    \$jenkinsUnityText = if (\$_ -is [System.Management.Automation.ErrorRecord]) {\n" +
        "        @(\$_.Exception.Message)\n" +
        "    } else {\n" +
        "        \$_ | Out-String -Stream\n" +
        "    }\n" +
        "    foreach (\$jenkinsUnityLine in \$jenkinsUnityText) {\n" +
        "        if (\$_ -is [System.Management.Automation.ErrorRecord]) {\n" +
        "            [Console]::Out.WriteLine(\$jenkinsUnityLine)\n" +
        "        } else {\n" +
        "            [System.IO.File]::AppendAllText(\$jenkinsUnityOutput, \$jenkinsUnityLine + [Environment]::NewLine, \$jenkinsUnityUtf8)\n" +
        "            [Console]::Out.WriteLine(\$jenkinsUnityLine)\n" +
        "        }\n" +
        "    }\n" +
        "}\n"
}

private void deleteOutputFile(String outputFile) {
    if (isWindows()) {
        powershell(
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
