def call(Closure<Boolean> predicate, Closure<Void> payload) {
	executeOnAll(predicate, false, payload)
}

def call(Closure<Boolean> predicate, Boolean runParallel, Closure<Void> payload) {
	def nodes = []
	Jenkins.instance.getNodes().each { node ->
		if (node.getComputer()?.isOnline() && predicate(node)) {
			nodes.add(node.getNodeName())
		}
	}

	if (runParallel) {
		def parallelStages = [:]
		for (String nodeName in nodes) {
			parallelStages[nodeName] = {
				stage("Switching to: ${nodeName}") {
					node(nodeName) {
						stage(env.NODE_NAME) {
							payload.call(env.NODE_NAME)
						}
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