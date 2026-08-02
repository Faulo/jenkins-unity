def call(String containerName, Closure body) {
    echo "withUnity cache probe v3: ${containerName}"

    body()
}
