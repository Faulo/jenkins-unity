import net.slothsoft.jenkins.unity.PreparedUnityPackage

void call(PreparedUnityPackage preparedPackage, Closure body) {
    if (!preparedPackage) {
        throw new IllegalArgumentException('preparedPackage must not be null')
    }

    def options = preparedPackage.options
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
    if (options.unityManifestCredentialsId) {
        credentials << file(credentialsId: options.unityManifestCredentialsId, variable: 'UNITY_EMPTY_MANIFEST')
        forwardedEnvironment << 'UNITY_EMPTY_MANIFEST'
    }

    withCredentials(credentials) {
        def existingEnvironment = (env.JENKINS_UNITY_ENV ?: '').tokenize(':')
        def environmentNames = (existingEnvironment + forwardedEnvironment).findAll { it }.unique()
        withEnv(["JENKINS_UNITY_ENV=${environmentNames.join(':')}"]) {
            withUnity(body)
        }
    }
}
