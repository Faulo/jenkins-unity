import net.slothsoft.jenkins.unity.UnityProjectOptions

void call(UnityProjectOptions options, Closure body) {
    if (!options) {
        throw new IllegalArgumentException('options must not be null')
    }

    def credentials = []
    def forwardedEnvironment = []
    if (options.unityCredentialsId) {
        credentials << usernamePassword(credentialsId: options.unityCredentialsId, usernameVariable: 'UNITY_CREDENTIALS_USR', passwordVariable: 'UNITY_CREDENTIALS_PSW')
        forwardedEnvironment.addAll(['UNITY_CREDENTIALS_USR', 'UNITY_CREDENTIALS_PSW'])
    }
    if (options.emailCredentialsId) {
        credentials << usernamePassword(credentialsId: options.emailCredentialsId, usernameVariable: 'EMAIL_CREDENTIALS_USR', passwordVariable: 'EMAIL_CREDENTIALS_PSW')
        forwardedEnvironment.addAll(['EMAIL_CREDENTIALS_USR', 'EMAIL_CREDENTIALS_PSW'])
    }

    withCredentials(credentials) {
        def existingEnvironment = (env.JENKINS_UNITY_ENV ?: '').tokenize(':')
        def environmentNames = (existingEnvironment + forwardedEnvironment).findAll { it }.unique()
        withEnv(["JENKINS_UNITY_ENV=${environmentNames.join(':')}"]) {
            withUnity(body)
        }
    }
}
