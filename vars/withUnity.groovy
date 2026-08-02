def call(String containerName, Closure body) {
    echo "withUnity stub: ${containerName}"

    body()
}
