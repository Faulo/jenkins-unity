package net.slothsoft.jenkins.unity

final class UnityProjectContext implements Serializable {
    private static final long serialVersionUID = 1L

    final UnityProjectOptions options
    final String projectId
    final String version
    final String branch
    final String workspaceDirectory
    final String projectDirectory
    final String reportsDirectory
    final String documentationDirectory

    UnityProjectContext(UnityProjectOptions options, String projectId, String version, String branch, String workspaceDirectory, String projectDirectory, String reportsDirectory) {
        if (!options || !projectId || !branch || !workspaceDirectory || !projectDirectory || !reportsDirectory) {
            throw new IllegalArgumentException('Unity project context must not be empty')
        }
        this.options = options
        this.projectId = projectId
        this.version = version ?: '?'
        this.branch = branch
        this.workspaceDirectory = workspaceDirectory
        this.projectDirectory = projectDirectory
        this.reportsDirectory = reportsDirectory
        documentationDirectory = "${projectDirectory}/.Documentation"
    }
}
