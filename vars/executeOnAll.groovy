import jenkins.model.Jenkins

def call(Closure<Boolean> predicate, Closure<Void> payload) {
    call(predicate, false, payload)
}

def call(Closure<Boolean> predicate, Boolean runParallel, Closure<Void> payload) {
    def nodes = findNodeNames(predicate)

    if (runParallel) {
        def parallelStages = [:]
        for (String n in nodes) {
            def nodeName = n
            parallelStages[nodeName] = {
                stage(nodeName) {
                    node(nodeName) {
                        payload.call(nodeName)
                    }
                }
            }
        }

        parallel parallelStages
    } else {
        while (!nodes.isEmpty()) {
            def nodeExpression = nodes.collect { name -> "\"${name}\"" }.join(' || ')
            echo "Next free node will be selected from: ${nodeExpression}"

            stage("Switching to: ${nodeExpression}") {
                node("\"${nodeExpression}\"") {
                    nodes.remove(env.NODE_NAME)

                    stage(env.NODE_NAME) {
                        payload.call(env.NODE_NAME)
                    }
                }
            }
        }
    }
}

private List<String> findNodeNames(Closure<Boolean> predicate) {
    def nodes = []

    def jenkins = Jenkins.getInstanceOrNull()

    if (jenkins == null) {
        return nodes
    }

    if (jenkins.getNumExecutors() > 0 && predicate.call(jenkins)) {
        nodes.add('built-in')
    }

    for (def currentNode in jenkins.getNodes()) {
        if (currentNode.getComputer()?.isOnline() && predicate.call(currentNode)) {
            nodes.add(currentNode.getNodeName())
        }
    }

    return nodes
}