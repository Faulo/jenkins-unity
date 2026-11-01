def call(String body, String file = '') {
    def launcher = env.COMPOSER_EXECUTABLE ?: env.COMPOSE_UNITY ?: 'composer'
    return execStdout(script: "${launcher} ${body}", stdout: file)
}
