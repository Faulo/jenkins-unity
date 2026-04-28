import hudson.model.Label
import hudson.console.HyperlinkNote
import jenkins.model.Jenkins

def call(String labelExpression, Closure body) {
    def nodeName = env.NODE_NAME
    def jenkins = Jenkins.get()

    if (jenkins && nodeMatchesLabel(nodeName, labelExpression, jenkins)) {
        def computer = jenkins.getComputer(nodeName)
        def hyperlink = computer ? HyperlinkNote.encodeTo(jenkins.getRootUrl() + computer.getUrl(), nodeName) : nodeName
        echo "Continuing on ${hyperlink} as it matches '${labelExpression}' in ${pwd()}"

        body()
    } else {
        node(labelExpression) {
            body()
        }
    }
}

private boolean nodeMatchesLabel(String nodeName, String labelExpression, Jenkins jenkins) {
    if (!nodeName) {
        return false
    }

    if (nodeName == labelExpression) {
        return true
    }

    def node = jenkins.getNode(nodeName)
    def label = Label.parseExpression(labelExpression)

    return node && label && label.matches(node)
}