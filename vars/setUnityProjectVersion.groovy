import net.slothsoft.jenkins.unity.UnityProjectContext
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

void call(UnityProjectContext project) {
    if (!project) {
        throw new IllegalArgumentException('project must not be null')
    }
    try {
        withUnityProjectEnvironment(project.options) {
            callUnity "unity-project-version '${project.projectDirectory}' set '${project.version}'"
        }
    } catch (FlowInterruptedException e) {
        currentBuild.result = e.result
        throw e
    }
}
