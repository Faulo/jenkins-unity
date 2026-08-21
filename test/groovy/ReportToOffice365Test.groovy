import com.lesfurets.jenkins.unit.BasePipelineTest
import hudson.model.Result
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertSame
import static org.junit.jupiter.api.Assertions.assertThrows

class ReportToOffice365Test extends BasePipelineTest {
    @BeforeEach
    void configurePipelineTest() {
        super.setUp()
    }

    @Test
    void sendsStatusAndChanges() {
        def entries = [
            new Expando(msg: 'First change'),
            new Expando(msg: 'Second change')
        ]
        def changeSet = new Expando()
        changeSet.getItems = { entries }
        def currentBuild = new Expando(
            currentResult: 'UNSTABLE',
            changeSets: [changeSet]
        )

        Map invocation = [:]
        helper.registerAllowedMethod('office365ConnectorSend', [Map]) { Map arguments ->
            invocation = arguments
        }

        def reportToOffice365 = loadScript('vars/reportToOffice365.groovy')
        reportToOffice365.call('https://office.example/webhook', currentBuild, 'Example Build')

        assertEquals('https://office.example/webhook', invocation.webhookUrl)
        assertEquals('- First change\r\n- Second change\r\n', invocation.message.toString())
        assertEquals('UNSTABLE: Example Build', invocation.status.toString())
    }

    @Test
    void sendsEmptyMessageWithoutChanges() {
        def currentBuild = new Expando(
            currentResult: 'SUCCESS',
            changeSets: []
        )

        Map invocation = [:]
        helper.registerAllowedMethod('office365ConnectorSend', [Map]) { Map arguments ->
            invocation = arguments
        }

        def reportToOffice365 = loadScript('vars/reportToOffice365.groovy')
        reportToOffice365.call('https://office.example/webhook', currentBuild, 'Example Build')

        assertEquals('', invocation.message)
        assertEquals('SUCCESS: Example Build', invocation.status.toString())
    }

    @Test
    void interruptionPropagates() {
        def currentBuild = new Expando(
            currentResult: 'ABORTED',
            changeSets: []
        )
        def interruption = new FlowInterruptedException(Result.ABORTED, true)
        helper.registerAllowedMethod('office365ConnectorSend', [Map]) { Map arguments ->
            throw interruption
        }

        def reportToOffice365 = loadScript('vars/reportToOffice365.groovy')
        def thrown = assertThrows(FlowInterruptedException) {
            reportToOffice365.call('https://office.example/webhook', currentBuild, 'Example Build')
        }

        assertSame(interruption, thrown)
    }
}
