def call(Closure body) {
    withEnvFile(".env", body)
}

def call(String envFile, Closure body) {
    withEnv(readFile(envFile).split('\n') as List) {
        body()
    }
}
