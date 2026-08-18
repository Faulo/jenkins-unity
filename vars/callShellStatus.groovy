def call(String script, Boolean echoScript = false) {
    return execStatus(script: script, echoScript: echoScript) as int
}
