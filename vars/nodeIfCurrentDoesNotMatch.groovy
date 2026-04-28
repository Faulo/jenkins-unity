import hudson.model.Label
import jenkins.model.Jenkins

def call(String labelExpression, Closure body) {
    if (env.NODE_NAME && nodeMatchesLabel(env.NODE_NAME, labelExpression)) {
        body()
    } else {
        node(labelExpression) {
            body()
        }
    }
}

private boolean nodeMatchesLabel(String nodeName, String labelExpression) {
    if (nodeName == labelExpression) {
        return true
    }

    def jenkins = Jenkins.get()
    def node = jenkins.getNode(nodeName)
    def label = Label.parseExpression(labelExpression)

    return node && label && label.matches(node)
}