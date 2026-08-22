import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.JenkinsSessionRule

class UnityPackageCpsTest {
    @Rule
    public JenkinsSessionRule sessions = new JenkinsSessionRule()

    @Test
    void preparedPackageSurvivesControllerRestart() {
        sessions.then { JenkinsRule jenkins ->
            def job = jenkins.createProject(WorkflowJob, 'prepared-package-restart')
            job.definition = new CpsFlowDefinition('''
                import net.slothsoft.jenkins.unity.PreparedUnityPackage
                import net.slothsoft.jenkins.unity.UnityPackageContext
                import net.slothsoft.jenkins.unity.UnityPackageOptions

                def options = UnityPackageOptions.fromMap([
                    VALIDATE_CHANGELOG: false,
                    CHECK_FORMATTING: false,
                    PUBLISH_BRANCHES: ['main'],
                ])
                def context = new UnityPackageContext('net.example.package', '1.2.3-preview.1', 'main', '.')
                def prepared = new PreparedUnityPackage(options, context, 'execution', 'source', 'configuration')
                sleep time: 3, unit: 'SECONDS'
                echo "restored ${prepared.context.packageId}@${prepared.context.version} on ${prepared.context.branch}"
            '''.stripIndent(), false)
            def build = job.scheduleBuild2(0).waitForStart()
            jenkins.waitForMessage('Sleeping for 3 sec', build)
        }

        sessions.then { JenkinsRule jenkins ->
            def job = jenkins.jenkins.getItemByFullName('prepared-package-restart', WorkflowJob)
            def build = jenkins.waitForCompletion(job.lastBuild)
            jenkins.assertBuildStatusSuccess(build)
            jenkins.assertLogContains('restored net.example.package@1.2.3-preview.1 on main', build)
        }
    }
}
