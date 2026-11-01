import hudson.model.Result
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertSame
import static org.junit.jupiter.api.Assertions.assertThrows

class CiTestLayoutTest {
    @Test
    void runsMavenThenOneIntegrationInvocationPerOsAndContinuesAfterFailure() {
        def events = []
        def script = pipeline(events, new IllegalStateException('Linux test failure'))
        script.run()
        assertEquals([
            'image:maven:3-eclipse-temurin-21',
            'label:docker && linux',
            'exec:mvn --batch-mode clean test',
            'junit:target/surefire-reports/TEST-*.xml',
            'node:unity && linux', 'checkout', 'timeout:120', 'tests:linux', 'failed',
            'node:unity && windows', 'checkout', 'timeout:120', 'tests:windows',
        ], events)
    }

    @Test
    void propagatesInterruptionWithoutStartingAnotherOs() {
        def events = []
        def interruption = new FlowInterruptedException(Result.ABORTED, true)
        def script = pipeline(events, interruption)
        assertSame(interruption, assertThrows(FlowInterruptedException) { script.run() })
        assertEquals('tests:linux', events.last())
    }

    private static def pipeline(List events, Throwable linuxFailure) {
        def script = new GroovyShell(new Binding([none: 'none', scm: 'scm'])).parse(new File('.jenkins/Jenkinsfile.groovy'))
        script.metaClass.readTrusted = { String path -> new File(path).getText('UTF-8') }
        script.metaClass.readProperties = { Map args ->
            def properties = new Properties()
            properties.load(new StringReader(args.text))
            properties.collectEntries { key, value -> [(key.toString()): value.toString()] }
        }
        for (String method : ['pipeline', 'options', 'stages', 'steps', 'post', 'always', 'script', 'docker']) {
            script.metaClass."${method}" = { Closure body -> body() }
        }
        script.metaClass.agent = { Object value -> if (value instanceof Closure) { value() } }
        for (String method : ['disableConcurrentBuilds', 'disableResume', 'disableRestartFromStage']) {
            script.metaClass."${method}" = { -> }
        }
        script.metaClass.stage = { String name, Closure body -> body() }
        script.metaClass.image = { String value -> events << "image:${value}".toString() }
        script.metaClass.label = { String value -> events << "label:${value}".toString() }
        script.metaClass.exec = { String value -> events << "exec:${value}".toString() }
        script.metaClass.junit = { Map args -> events << "junit:${args.testResults}".toString() }
        script.metaClass.node = { String label, Closure body ->
            events << "node:${label}".toString()
            body()
        }
        script.metaClass.checkout = { Object ignored -> events << 'checkout' }
        script.metaClass.timeout = { Map args, Closure body ->
            events << "timeout:${args.time}".toString()
            body()
        }
        script.metaClass.catchError = { Map args, Closure body ->
            assertEquals(false, args.catchInterruptions)
            try {
                body()
            } catch (FlowInterruptedException e) {
                throw e
            } catch (Throwable e) {
                events << 'failed'
            }
        }
        script.metaClass.load = { String path ->
            assertEquals('.jenkins/tests/integration.groovy', path)
            new Expando(call: { Map config, String platform ->
                events << "tests:${platform}".toString()
                if (platform == 'linux') {
                    throw linuxFailure
                }
            })
        }
        script
    }
}
