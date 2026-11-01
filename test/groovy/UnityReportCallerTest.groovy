import com.lesfurets.jenkins.unit.BasePipelineTest
import hudson.model.Result
import net.slothsoft.jenkins.unity.PreparedUnityPackage
import net.slothsoft.jenkins.unity.UnityPackageContext
import net.slothsoft.jenkins.unity.UnityPackageOptions
import net.slothsoft.jenkins.unity.UnityProjectContext
import net.slothsoft.jenkins.unity.UnityProjectOptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertSame
import static org.junit.jupiter.api.Assertions.assertThrows

class UnityReportCallerTest extends BasePipelineTest {
    @BeforeEach
    void configurePipelineTest() {
        super.setUp()
        binding.setVariable('currentBuild', new Expando(result: null))
        helper.registerAllowedMethod('dir', [String, Closure]) { String ignored, Closure body -> body() }
        helper.registerAllowedMethod('fileExists', [String]) { String ignored -> false }
        helper.registerAllowedMethod('withUnityProjectEnvironment', [UnityProjectOptions, Closure]) { UnityProjectOptions ignored, Closure body -> body() }
    }

    @Test
    void projectBuildAndTestFailuresPublishReportsBeforePropagating() {
        def options = UnityProjectOptions.fromMap([:])
        def project = new UnityProjectContext(options, 'Game', '1.0', 'main', '/workspace', '/workspace/Game', '/reports')
        def failure = new IllegalStateException('tool exit 7')
        def publications = []
        boolean reportWritten = false
        helper.registerAllowedMethod('callUnity', [String, String]) { String command, String report ->
            reportWritten = true
            throw failure
        }
        helper.registerAllowedMethod('fileExists', [String]) { String ignored -> reportWritten }
        helper.registerAllowedMethod('junit', [Map]) { Map report -> publications << report }
        def build = loadScript('vars/buildUnityProject.groovy')
        for (String operation : ['solution', 'windows', 'linux', 'mac', 'webgl', 'android']) {
            publications.clear()
            reportWritten = false
            assertSame(failure, assertThrows(IllegalStateException) { build.call(project, operation) })
            assertEquals(1, publications.size())
        }
        publications.clear()
        reportWritten = false
        def test = loadScript('vars/testUnityProject.groovy')
        assertSame(failure, assertThrows(IllegalStateException) { test.call(project, 'unity') })
        assertEquals([[testResults: 'tests.xml', allowEmptyResults: true]], publications)
    }

    @Test
    void installFailurePublishesBeforeDeletingTheInstallation() {
        def events = []
        helper.registerAllowedMethod('pwd', [Map]) { Map ignored -> '/workspace@tmp' }
        helper.registerAllowedMethod('deleteDir', []) { events << 'cleanup' }
        helper.registerAllowedMethod('unstash', [String]) { String ignored -> }
        helper.registerAllowedMethod('withUnityPackageEnvironment', [PreparedUnityPackage, String, Closure]) { PreparedUnityPackage ignored, String ignoredPath, Closure body -> body() }
        def failure = new IllegalStateException('install failed')
        helper.registerAllowedMethod('callUnity', [String, String]) { String command, String report ->
            events << 'command'
            throw failure
        }
        helper.registerAllowedMethod('fileExists', [String]) { String ignored -> events.contains('command') }
        helper.registerAllowedMethod('junit', [Map]) { Map report -> events << report.testResults }
        def options = UnityPackageOptions.fromMap([:])
        def context = new UnityPackageContext('net.example.package', '1.0.0', 'main', '.')
        def prepared = new PreparedUnityPackage(options, context, 'execution', 'source', 'configuration')
        def install = loadScript('vars/installUnityPackage.groovy')
        assertSame(failure, assertThrows(IllegalStateException) { install.call(prepared) })
        assertEquals(['cleanup', 'command', 'package-install.xml', 'cleanup'], events)
    }

    @Test
    void legacyMapAndClosureFormsKeepTheirFailureAndInterruptionPolicies() {
        def currentBuild = binding.getVariable('currentBuild')
        binding.setVariable('env', [BRANCH_NAME: 'main'])
        binding.setVariable('WORKSPACE', '/workspace')
        binding.setVariable('WORKSPACE_TMP', '/workspace@tmp')
        binding.setVariable('NODE_NAME', 'test-node')
        helper.registerAllowedMethod('pwd', []) { '/workspace' }
        helper.registerAllowedMethod('stage', [String, Closure]) { String ignored, Closure body -> body() }
        helper.registerAllowedMethod('deleteDir', []) { }
        helper.registerAllowedMethod('withCredentials', [List, Closure]) { List ignored, Closure body -> body() }
        helper.registerAllowedMethod('withEnv', [List, Closure]) { List ignored, Closure body -> body() }
        helper.registerAllowedMethod('nodeIfCurrentDoesNotMatch', [String, Closure]) { String ignored, Closure body -> body() }
        helper.registerAllowedMethod('stash', [Map]) { Map ignored -> }
        helper.registerAllowedMethod('unstash', [String]) { String ignored -> }
        helper.registerAllowedMethod('echo', [String]) { String ignored -> }
        helper.registerAllowedMethod('writeFile', [Map]) { Map ignored -> }
        helper.registerAllowedMethod('fileExists', [String]) { String ignored -> true }
        helper.registerAllowedMethod('callComposer', [String]) { String ignored -> 'Game' }
        def publications = []
        helper.registerAllowedMethod('junit', [Map]) { Map report -> publications << report.testResults }
        Throwable commandFailure = new IllegalStateException('tool failed')
        helper.registerAllowedMethod('callUnity', [String, String]) { String ignored, String ignoredReport -> throw commandFailure }
        for (String name : ['unityProject', 'unityPackage']) {
            def step = loadScript("vars/${name}.groovy")
            helper.registerAllowedMethod(name, [Map]) { Map args -> step.call(args) }
            for (boolean closureForm : [false, true]) {
                publications.clear()
                currentBuild.result = null
                commandFailure = new IllegalStateException('tool failed')
                def invoke = {
                    if (closureForm) {
                        step.call {
                            ID = 'net.example.package'
                            VERSION = '1.0.0'
                            TEST_UNITY = '1'
                        }
                    } else {
                        step.call([ID: 'net.example.package', VERSION: '1.0.0', TEST_UNITY: '1'])
                    }
                }
                invoke()
                assertEquals('UNKNOWN', currentBuild.result)
                assertEquals([name == 'unityProject' ? 'tests.xml' : 'package-install.xml'], publications)
                publications.clear()
                commandFailure = new FlowInterruptedException(Result.ABORTED, true)
                assertSame(commandFailure, assertThrows(FlowInterruptedException) { invoke() })
                assertEquals(Result.ABORTED, currentBuild.result)
                assertEquals([], publications)
            }
        }
    }
}
