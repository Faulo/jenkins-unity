import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonSlurperClassic
import hudson.model.Result
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertSame
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class ReportToDiscordTest extends BasePipelineTest {
    @BeforeEach
    void configurePipelineTest() {
        System.setProperty('groovy.json.faststringutils.disable', 'true')
        super.setUp()
        binding.setVariable('env', [
            BUILD_URL: 'https://ci.example/job/example/12/',
            DISCORD_PING_IF: 'UNSTABLE',
            DISCORD_PING_USER: '123456'
        ])
    }

    @Test
    void sendsPayloadUsingEnvironmentDefaults() {
        def currentBuild = createBuild('FAILURE')
        currentBuild.rawBuild.execution.causeOfFailure = 'Compilation failed'
        currentBuild.rawBuild.culprits = [new Expando(displayName: 'Ada')]
        currentBuild.changeSets = [new Expando(items: [new Expando(msg: 'Fix build')])]
        currentBuild.resultIsWorseOrEqualTo = { String threshold ->
            assertEquals('UNSTABLE', threshold)
            true
        }

        Map invocation = [:]
        helper.registerAllowedMethod('httpRequest', [Map]) { Map arguments ->
            invocation = arguments
        }

        def reportToDiscord = loadScript('vars/reportToDiscord.groovy')
        reportToDiscord.call('https://discord.example/webhook', currentBuild, 'Example Build')

        assertEquals('POST', invocation.httpMode)
        assertEquals('https://discord.example/webhook', invocation.url)
        assertEquals('200:299', invocation.validResponseCodes)
        assertEquals(30, (int) invocation.timeout)
        assertTrue((boolean) invocation.quiet)
        assertFalse((boolean) invocation.consoleLogResponseBody)

        Map payload = (Map) new JsonSlurperClassic().parseText((String) invocation.requestBody)
        List embeds = (List) payload.embeds
        Map embed = (Map) embeds[0]
        assertEquals('FAILURE: Example Build', embed.title)
        assertEquals('https://ci.example/job/example/12/', embed.url)
        assertEquals(0xE74C3C, (int) embed.color)
        assertEquals('Cause of failure:\r\nCompilation failed\r\nHelp!\r\n<@123456>\r\n', embed.description)
        assertEquals('Changes:\r\n- Fix build\r\n\r\nCulprits:\r\n- Ada', embed.footer.text)
    }

    @Test
    void fourArgumentFormUsesEnvironmentPingUser() {
        def currentBuild = createBuild('FAILURE')
        currentBuild.resultIsWorseOrEqualTo = { String threshold ->
            assertEquals('FAILURE', threshold)
            true
        }

        Map invocation = [:]
        helper.registerAllowedMethod('httpRequest', [Map]) { Map arguments ->
            invocation = arguments
        }

        def reportToDiscord = loadScript('vars/reportToDiscord.groovy')
        reportToDiscord.call('https://discord.example/webhook', currentBuild, 'Example Build', 'FAILURE')

        Map payload = (Map) new JsonSlurperClassic().parseText((String) invocation.requestBody)
        List embeds = (List) payload.embeds
        Map embed = (Map) embeds[0]
        assertEquals('Help!\r\n<@123456>\r\n', embed.description)
    }

    @Test
    void usesDocumentedColorsAndTruncationLimits() {
        def reportToDiscord = loadScript('vars/reportToDiscord.groovy')

        assertEquals(0x19A719, (int) reportToDiscord.discordColorForResult('SUCCESS'))
        assertEquals(0xF1C40F, (int) reportToDiscord.discordColorForResult('UNSTABLE'))
        assertEquals(0xE74C3C, (int) reportToDiscord.discordColorForResult('FAILURE'))
        assertEquals(0x95A5A6, (int) reportToDiscord.discordColorForResult('ABORTED'))
        assertEquals(0x95A5A6, (int) reportToDiscord.discordColorForResult('NOT_BUILT'))
        assertEquals(0x3498DB, (int) reportToDiscord.discordColorForResult('UNKNOWN'))
        assertEquals('', reportToDiscord.truncateDiscord(null, 5))
        assertEquals('12345', reportToDiscord.truncateDiscord('12345', 5))
        assertEquals('1234…', reportToDiscord.truncateDiscord('123456', 5))
    }

    @Test
    void invalidPingThresholdIsLoggedAndTreatedAsPing() {
        def currentBuild = createBuild('FAILURE')
        currentBuild.resultIsWorseOrEqualTo = { String threshold ->
            throw new IllegalArgumentException("Unknown result '${threshold}'")
        }
        def messages = []
        helper.registerAllowedMethod('echo', [String]) { String message ->
            messages.add(message)
        }

        def reportToDiscord = loadScript('vars/reportToDiscord.groovy')

        assertTrue((boolean) reportToDiscord.buildShouldPing(currentBuild, 'BROKEN'))
        assertEquals([
            "Invalid DISCORD_PING_IF='BROKEN': IllegalArgumentException: Unknown result 'BROKEN'"
        ], messages)
    }

    @Test
    void ordinaryRequestFailureIsLoggedAndSwallowed() {
        def currentBuild = createBuild('SUCCESS')
        currentBuild.resultIsWorseOrEqualTo = { false }
        def messages = []
        helper.registerAllowedMethod('httpRequest', [Map]) { Map arguments ->
            throw new IllegalStateException('Network unavailable')
        }
        helper.registerAllowedMethod('echo', [String]) { String message ->
            messages.add(message)
        }

        def reportToDiscord = loadScript('vars/reportToDiscord.groovy')
        reportToDiscord.call('https://discord.example/webhook', currentBuild, 'Example Build', 'FAILURE', '')

        assertEquals([
            'Discord notification failed: IllegalStateException: Network unavailable'
        ], messages)
    }

    @Test
    void requestInterruptionSetsResultAndPropagates() {
        def currentBuild = createBuild('SUCCESS')
        currentBuild.resultIsWorseOrEqualTo = { false }
        def interruption = new FlowInterruptedException(Result.ABORTED, true)
        helper.registerAllowedMethod('httpRequest', [Map]) { Map arguments ->
            throw interruption
        }

        def reportToDiscord = loadScript('vars/reportToDiscord.groovy')
        def thrown = assertThrows(FlowInterruptedException) {
            reportToDiscord.call('https://discord.example/webhook', currentBuild, 'Example Build', 'FAILURE', '')
        }

        assertSame(interruption, thrown)
        assertEquals(Result.ABORTED, currentBuild.result)
    }

    @Test
    void pingCheckInterruptionSetsResultAndPropagates() {
        def currentBuild = createBuild('SUCCESS')
        def interruption = new FlowInterruptedException(Result.ABORTED, true)
        currentBuild.resultIsWorseOrEqualTo = { String threshold ->
            throw interruption
        }

        def reportToDiscord = loadScript('vars/reportToDiscord.groovy')
        def thrown = assertThrows(FlowInterruptedException) {
            reportToDiscord.buildShouldPing(currentBuild, 'FAILURE')
        }

        assertSame(interruption, thrown)
        assertEquals(Result.ABORTED, currentBuild.result)
    }

    private static Expando createBuild(String result) {
        new Expando(
            currentResult: result,
            result: null,
            changeSets: [],
            rawBuild: new Expando(
                execution: new Expando(causeOfFailure: null),
                culprits: []
            )
        )
    }
}
