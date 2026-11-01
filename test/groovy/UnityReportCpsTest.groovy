import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.JenkinsSessionRule

class UnityReportCpsTest {
    @Rule
    public JenkinsSessionRule sessions = new JenkinsSessionRule()

    @Test
    void failureSurvivesRestartDuringReportPublication() {
        sessions.then { JenkinsRule jenkins ->
            def job = jenkins.createProject(WorkflowJob, 'unity-report-restart')
            // Compile the library source with CPS, as a real Shared Library does.
            def source = new File('src/net/slothsoft/jenkins/unity/UnityReports.groovy').text.replace('package net.slothsoft.jenkins.unity', '')
            job.definition = new CpsFlowDefinition(source + '''
                def fileExists(String path) { false }
                def junit(Map options) {
                    echo "publishing ${options.testResults}"
                    sleep time: 3, unit: 'SECONDS'
                    echo 'publication resumed'
                }
                // Simulate a report produced just before a command failure.
                boolean reportExists = false
                def steps = this
                def reportSteps = [
                    fileExists: { String path -> reportExists },
                    junit: { Map options -> steps.junit(options) }
                ]
                try {
                    UnityReports.run(reportSteps, [testResults: 'tests.xml']) {
                        reportExists = true
                        error 'original command failure'
                    }
                    error 'expected failure'
                } catch (Throwable failure) {
                    assert failure.message == 'original command failure'
                    echo 'original failure preserved'
                }
            '''.stripIndent(), false)
            def build = job.scheduleBuild2(0).waitForStart()
            jenkins.waitForMessage('Sleeping for 3 sec', build)
        }
        sessions.then { JenkinsRule jenkins ->
            def job = jenkins.jenkins.getItemByFullName('unity-report-restart', WorkflowJob)
            def build = jenkins.waitForCompletion(job.lastBuild)
            jenkins.assertBuildStatusSuccess(build)
            jenkins.assertLogContains('publication resumed', build)
            jenkins.assertLogContains('original failure preserved', build)
        }
    }
}
