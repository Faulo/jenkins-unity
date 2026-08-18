import hudson.FilePath
import hudson.model.Run
import hudson.model.TaskListener
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration
import org.jenkinsci.plugins.workflow.libs.LibraryRetriever
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension

import java.security.Security

import static org.junit.jupiter.api.Assertions.assertNotNull

class WithEnvFileTest {
    @RegisterExtension
    final JenkinsSessionExtension sessions = new JenkinsSessionExtension()

    @Test
    void appliesEnvironmentFromDefaultFile() throws Throwable {
        if (!Security.getProvider('BC')) {
            Security.addProvider(new BouncyCastleProvider())
        }

        sessions.then { JenkinsRule jenkins ->
            LibraryConfiguration library = new LibraryConfiguration('jenkins-unity', new LocalLibraryRetriever('.'))
            library.defaultVersion = 'test'
            library.implicit = true
            GlobalLibraries.get().libraries = [library]

            WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob, 'with-env-file')
            def workspace = jenkins.jenkins.getWorkspaceFor(job)
            assertNotNull(workspace)
            workspace.mkdirs()
            workspace.child('.env').write(resource('/withEnvFile/.env'), 'UTF-8')

            job.definition = new CpsFlowDefinition('''
node {
    withEnvFile {
        if (env.ALPHA != 'one') {
            error "Expected ALPHA to be 'one', got '${env.ALPHA}'"
        }
        if (env.BETA != 'two words') {
            error "Expected BETA to be 'two words', got '${env.BETA}'"
        }
    }
}
''', false)

            WorkflowRun run = job.scheduleBuild2(0).get()
            jenkins.assertBuildStatusSuccess(run)
        }
    }

    private String resource(String name) {
        def resource = getClass().getResource(name)
        assertNotNull(resource)
        return resource.getText('UTF-8')
    }

    private static class LocalLibraryRetriever extends LibraryRetriever {
        private final String sourceDirectory

        LocalLibraryRetriever(String sourceDirectory) {
            this.sourceDirectory = new File(sourceDirectory).canonicalPath
        }

        @Override
        void retrieve(String name, String version, boolean changelog, FilePath target, Run run, TaskListener listener) {
            retrieve(name, version, target, run, listener)
        }

        @Override
        void retrieve(String name, String version, FilePath target, Run run, TaskListener listener) {
            FilePath source = new FilePath(new File(sourceDirectory))
            source.child('vars').copyRecursiveTo(target.child('vars'))
        }
    }
}
