def call(String body, String file = '') {
    def parts = body.trim().split('\\s+', 2)
    def command = parts[0]
    // Preserve historical public invocations while callers migrate to the native API.
    if (command in ['unity-build', 'unity-empty-project', 'unity-method', 'unity-start',
                    'unity-module-install', 'unity-package-install', 'unity-tests', 'unity-help',
                    'unity-documentation', 'unity-project-setting', 'unity-project-version',
                    'autoversion', 'steam-login', 'steam-buildfile', 'transform-dotnet-format']) {
        return callComposer("exec ${body}", file)
    }

    def invocation = body
    if (file && command in ['build', 'empty-project', 'method', 'start', 'module-install', 'package-install', 'tests']) {
        // Report-only stdout keeps the captured return value and file free of normal logs.
        invocation = "${command} --junit -${parts.length > 1 ? ' ' + parts[1] : ''}"
    }
    return execStdout(script: "${env.UNITY_EXECUTABLE ?: 'unity'} ${invocation}", stdout: file)
}
