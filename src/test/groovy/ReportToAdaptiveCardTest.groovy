import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class ReportToAdaptiveCardTest extends BasePipelineTest {
    @BeforeEach
    void configurePipelineTest() {
        System.setProperty('groovy.json.faststringutils.disable', 'true')
        super.setUp()
        binding.setVariable('env', [
            BUILD_NUMBER: '12',
            JOB_BASE_NAME: 'example',
            BUILD_URL: 'https://ci.example/job/example/12/'
        ])
        helper.registerAllowedMethod('writeJSON', [Map]) { Map arguments ->
            JsonOutput.toJson(arguments.json)
        }
    }

    @Test
    void sendsDetailedFailurePayload() {
        def testResult = new Expando(totalCount: 8, failCount: 2)
        def rawBuild = new Expando(
            culprits: [new Expando(displayName: 'Ada')],
            execution: new Expando(causeOfFailure: 'Compilation failed')
        )
        rawBuild.getAction = { Class actionType -> testResult }
        def currentBuild = new Expando(
            currentResult: 'FAILURE',
            rawBuild: rawBuild,
            changeSets: [new Expando(items: [new Expando(msg: 'Fix build')])]
        )

        Map invocation = [:]
        def messages = []
        helper.registerAllowedMethod('httpRequest', [Map]) { Map arguments ->
            invocation = arguments
            new Expando(content: 'accepted')
        }
        helper.registerAllowedMethod('echo', [String]) { String message ->
            messages.add(message)
        }

        def reportToAdaptiveCard = loadScript('vars/reportToAdaptiveCard.groovy')
        reportToAdaptiveCard.call('https://office.example/webhook', currentBuild, 'Example Build')

        assertEquals('POST', invocation.httpMode)
        assertEquals('APPLICATION_JSON_UTF8', invocation.contentType)
        assertEquals('https://office.example/webhook', invocation.url)
        assertEquals(['AdaptiveCard Response: accepted'], messages)

        Map payload = (Map) new JsonSlurperClassic().parseText((String) invocation.requestBody)
        assertEquals('⛔ Example Build: FAILURE', payload.summary)
        List attachments = (List) payload.attachments
        Map attachment = (Map) attachments[0]
        Map content = (Map) attachment.content
        assertEquals('AdaptiveCard', content.type)
        assertEquals('1.2', content.version)
        List entities = (List) ((Map) content.msteams).entities
        assertEquals([
            type: 'mention',
            text: '<at>Ada</at>',
            mentioned: [id: 'Ada', name: 'Ada']
        ], entities[0])

        List<String> texts = ((List) content.body).collect { block -> (String) ((Map) block).text }
        assertTrue(texts.contains('[Example Build](https://ci.example/job/example/)'))
        assertTrue(texts.contains('⛔ [example #12](https://ci.example/job/example/12/): **FAILURE**'))
        assertTrue(texts.contains('☠️ Failed tests: [**2**](https://ci.example/job/example/12/testReport/)'))
        assertTrue(texts.contains('- <at>Ada</at>\r'))
        assertTrue(texts.contains('Compilation failed'))
        assertTrue(texts.contains('- Fix build\r'))
    }

    @Test
    void reportsSuccessfulTestsAndNoChanges() {
        def testResult = new Expando(totalCount: 8, failCount: 0)
        def rawBuild = new Expando(
            culprits: [],
            execution: new Expando(causeOfFailure: null)
        )
        rawBuild.getAction = { Class actionType -> testResult }
        def currentBuild = new Expando(
            currentResult: 'SUCCESS',
            rawBuild: rawBuild,
            changeSets: []
        )

        Map invocation = [:]
        helper.registerAllowedMethod('httpRequest', [Map]) { Map arguments ->
            invocation = arguments
            new Expando(content: 'accepted')
        }
        helper.registerAllowedMethod('echo', [String]) {}

        def reportToAdaptiveCard = loadScript('vars/reportToAdaptiveCard.groovy')
        reportToAdaptiveCard.call('https://office.example/webhook', currentBuild, 'Example Build')

        Map payload = (Map) new JsonSlurperClassic().parseText((String) invocation.requestBody)
        List attachments = (List) payload.attachments
        Map attachment = (Map) attachments[0]
        Map content = (Map) attachment.content
        List<String> texts = ((List) content.body).collect { block -> (String) ((Map) block).text }
        assertTrue(texts.contains('🎉 All [8](https://ci.example/job/example/12/testReport/) tests passed.'))
        assertTrue(texts.contains('No changes detected.'))
        assertEquals([], ((Map) content.msteams).entities)
    }

    @Test
    void requestFailurePropagates() {
        def rawBuild = new Expando(
            culprits: [],
            execution: new Expando(causeOfFailure: null)
        )
        rawBuild.getAction = { Class actionType -> null }
        def currentBuild = new Expando(
            currentResult: 'SUCCESS',
            rawBuild: rawBuild,
            changeSets: []
        )
        helper.registerAllowedMethod('httpRequest', [Map]) { Map arguments ->
            throw new IllegalStateException('Network unavailable')
        }

        def reportToAdaptiveCard = loadScript('vars/reportToAdaptiveCard.groovy')

        def thrown = assertThrows(IllegalStateException) {
            reportToAdaptiveCard.call('https://office.example/webhook', currentBuild, 'Example Build')
        }
        assertEquals('Network unavailable', thrown.message)
    }
}
