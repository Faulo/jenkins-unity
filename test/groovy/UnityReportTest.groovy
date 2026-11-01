import hudson.model.Result
import net.slothsoft.jenkins.unity.UnityReports
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertSame
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue
import static org.junit.jupiter.api.Assertions.fail

class UnityReportTest {
    @Test
    void composerPreservesInvocationFormsAndCapturedOutput() {
        def env = [:]
        def composer = new GroovyShell(new Binding([env: env])).parse(new File('vars/callComposer.groovy'))
        def calls = []
        composer.metaClass.execStdout = { Map args ->
            calls << args
            'trimmed stdout'
        }
        assertEquals('trimmed stdout', composer.call('help'))
        assertEquals(null, env.COMPOSE_UNITY)
        env.COMPOSE_UNITY = 'composer -d C:/unity exec'
        for (String file : ['nested/report.xml', '', null]) {
            assertEquals('trimmed stdout', composer.call('tool', file))
            assertEquals(file, calls.last().stdout)
            assertEquals('composer -d C:/unity exec tool', calls.last().script.toString())
        }
        assertEquals('composer help', calls.first().script.toString())
        env.COMPOSER_EXECUTABLE = 'preferred-composer'
        composer.call('exec utility')
        assertEquals('preferred-composer exec utility', calls.last().script.toString())
        def failure = new IllegalStateException('exit 7')
        composer.metaClass.execStdout = { Map ignored -> throw failure }
        assertSame(failure, assertThrows(IllegalStateException) { composer.call('tool', 'report.xml') })
        def interruption = new FlowInterruptedException(Result.ABORTED, true)
        composer.metaClass.execStdout = { Map ignored -> throw interruption }
        assertSame(interruption, assertThrows(FlowInterruptedException) { composer.call('tool') })
    }

    @Test
    void unityUsesNativeCommandsAndReportOnlyStdoutWithoutInitialization() {
        def env = [:]
        def unity = new GroovyShell(new Binding([env: env])).parse(new File('vars/callUnity.groovy'))
        def calls = []
        unity.metaClass.execStdout = { Map args ->
            calls << args
            'trimmed stdout'
        }
        unity.metaClass.callComposer = { String command -> fail('Native calls must not initialize Composer') }
        assertEquals('trimmed stdout', unity.call('--help'))
        assertEquals('unity --help', calls.last().script.toString())
        assertEquals('', calls.last().stdout)
        for (String command : ['build', 'empty-project', 'method', 'start', 'module-install', 'package-install', 'tests']) {
            assertEquals('trimmed stdout', unity.call("${command} 'project space' -- -custom", 'nested/report.xml'))
            assertEquals("unity ${command} --junit - 'project space' -- -custom".toString(), calls.last().script.toString())
            assertEquals('nested/report.xml', calls.last().stdout)
        }
        env.UNITY_EXECUTABLE = 'custom-unity'
        unity.call('tests project EditMode', null)
        assertEquals('custom-unity tests project EditMode', calls.last().script.toString())
        assertEquals(null, calls.last().stdout)
        unity.call('list', 'help.txt')
        assertEquals('custom-unity list', calls.last().script.toString())
        assertEquals('help.txt', calls.last().stdout)
        def failure = new IllegalStateException('exit 7')
        unity.metaClass.execStdout = { Map ignored -> throw failure }
        assertSame(failure, assertThrows(IllegalStateException) { unity.call('tests project', 'report.xml') })
        def interruption = new FlowInterruptedException(Result.ABORTED, true)
        unity.metaClass.execStdout = { Map ignored -> throw interruption }
        assertSame(interruption, assertThrows(FlowInterruptedException) { unity.call('tests project') })
    }

    @Test
    void historicalInvocationsRetainComposerArgumentsAndCapturedReturnValues() {
        def unity = new GroovyShell(new Binding([env: [:]])).parse(new File('vars/callUnity.groovy'))
        def calls = []
        unity.metaClass.callComposer = { String command, String file ->
            calls << [command, file]
            'captured compatibility output'
        }
        assertEquals('captured compatibility output', unity.call("unity-package-install 'package' 'workspace'", 'report.xml'))
        assertEquals([["exec unity-package-install 'package' 'workspace'", 'report.xml']], calls)
        unity.call('autoversion git project')
        assertEquals(['exec autoversion git project', ''], calls.last())
    }

    @Test
    void reportsPublishAfterSuccessAndNonzeroExitWithOriginalOptions() {
        def events = []
        def steps = reportSteps(events)
        def report = [testResults: 'tests.xml', allowEmptyResults: true]
        UnityReports.run(steps, report) { events << 'command' }
        assertEquals(['cleared', 'command', report], events)
        events.clear()
        def failure = new IllegalStateException('exit 7')
        assertSame(failure, assertThrows(IllegalStateException) {
            UnityReports.run(steps, report) {
                events << 'command'
                throw failure
            }
        })
        assertEquals(['cleared', 'command', report], events)
    }

    @Test
    void publicationFailureDoesNotReplaceCommandFailure() {
        def events = []
        def steps = reportSteps(events)
        def failure = new IllegalStateException('command failed')
        steps.junit = { Map ignored -> throw new IllegalArgumentException('invalid XML') }
        assertSame(failure, assertThrows(IllegalStateException) {
            UnityReports.run(steps, [testResults: 'tests.xml']) { throw failure }
        })
        assertTrue(events.contains('warning'))
        assertThrows(IllegalArgumentException) {
            UnityReports.run(steps, [testResults: 'tests.xml']) { }
        }
    }

    @Test
    void missingFailureReportsAreSkippedAndInterruptionsPropagate() {
        def events = []
        def steps = reportSteps(events)
        steps.fileExists = { String ignored -> false }
        def failure = new IllegalStateException('initialization failed')
        assertSame(failure, assertThrows(IllegalStateException) {
            UnityReports.run(steps, [testResults: 'tests.xml']) { throw failure }
        })
        assertTrue(events.empty)
        def interruption = new FlowInterruptedException(Result.ABORTED, true)
        assertSame(interruption, assertThrows(FlowInterruptedException) {
            UnityReports.run(steps, [testResults: 'tests.xml']) { throw interruption }
        })
        assertTrue(events.empty)
        steps.fileExists = { String ignored -> true }
        steps.junit = { Map ignored -> throw interruption }
        assertSame(interruption, assertThrows(FlowInterruptedException) {
            UnityReports.run(steps, [testResults: 'tests.xml']) { throw failure }
        })
    }

    private static def reportSteps(List events) {
        new Expando(
            fileExists: { String ignored -> true },
            writeFile: { Map args ->
                assertEquals('', args.text)
                events << 'cleared'
            },
            junit: { Map report -> events << report },
            echo: { String ignored -> events << 'warning' }
        )
    }
}
