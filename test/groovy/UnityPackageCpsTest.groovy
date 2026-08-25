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
    void packageLifecycleValuesSurviveControllerRestart() {
        sessions.then { JenkinsRule jenkins ->
            def job = jenkins.createProject(WorkflowJob, 'prepared-package-restart')
            job.definition = new CpsFlowDefinition('''
                import net.slothsoft.jenkins.unity.PreparedUnityPackage
                import net.slothsoft.jenkins.unity.InstalledUnityPackage
                import net.slothsoft.jenkins.unity.UnityPackageContext
                import net.slothsoft.jenkins.unity.UnityPackageOptions
                import net.slothsoft.jenkins.unity.UnityProjectContext
                import net.slothsoft.jenkins.unity.UnityProjectOptions

                def options = UnityPackageOptions.fromMap([
                    TEST_CHANGELOG: false,
                    TEST_FORMATTING: false,
                    PUBLISH_BRANCHES: ['main'],
                ])
                def context = new UnityPackageContext('net.example.package', '1.2.3-preview.1', 'main', '.')
                def prepared = new PreparedUnityPackage(options, context, 'execution', 'source', 'configuration')
                def installed = new InstalledUnityPackage(prepared, '/tmp/work', '/tmp/work/package', '/tmp/work/project', '/tmp/work/reports')
                def projectOptions = UnityProjectOptions.fromMap([PROJECT_BRANCH: 'main'])
                def project = new UnityProjectContext(projectOptions, 'Example Game', '1.2.3', 'main', '/workspace', '/workspace/Game', '/workspace@tmp/reports')
                sleep time: 3, unit: 'SECONDS'
                echo "restored ${installed.preparedPackage.context.packageId}@${installed.preparedPackage.context.version} in ${installed.projectDirectory}"
                echo "restored ${project.projectId}@${project.version} in ${project.projectDirectory}"
            '''.stripIndent(), false)
            def build = job.scheduleBuild2(0).waitForStart()
            jenkins.waitForMessage('Sleeping for 3 sec', build)
        }

        sessions.then { JenkinsRule jenkins ->
            def job = jenkins.jenkins.getItemByFullName('prepared-package-restart', WorkflowJob)
            def build = jenkins.waitForCompletion(job.lastBuild)
            jenkins.assertBuildStatusSuccess(build)
            jenkins.assertLogContains('restored net.example.package@1.2.3-preview.1 in /tmp/work/project', build)
            jenkins.assertLogContains('restored Example Game@1.2.3 in /workspace/Game', build)
        }
    }
}
