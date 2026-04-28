import hudson.model.Label
import hudson.console.HyperlinkNote
import jenkins.model.Jenkins

def call(String labelExpression, Closure body) {
    def nodeName = env.NODE_NAME

    if (nodeMatchesLabel(nodeName, labelExpression)) {
        def computer = Jenkins.get().getComputer(nodeName)
        def hyperlink = computer ? HyperlinkNote.encodeTo(computer.getUrl(), nodeName) : nodeName
        echo "Continuing on ${hyperlink} as it matches '${labelExpression}' in ${pwd()}"

        body()
    } else {
        node(labelExpression) {
            body()
        }
    }
}

private boolean nodeMatchesLabel(String nodeName, String labelExpression) {
    if (!nodeName) {
        return false
    }

    if (nodeName == labelExpression) {
        return true
    }

    def jenkins = Jenkins.get()
    def node = jenkins.getNode(nodeName)
    def label = Label.parseExpression(labelExpression)

    return node && label && label.matches(node)
}