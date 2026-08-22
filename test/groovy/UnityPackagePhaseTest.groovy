import com.lesfurets.jenkins.unit.BasePipelineTest
import hudson.model.Result
import net.slothsoft.jenkins.unity.PreparedUnityPackage
import net.slothsoft.jenkins.unity.UnityPackageContext
import net.slothsoft.jenkins.unity.UnityPackageOptions
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotEquals
import static org.junit.jupiter.api.Assertions.assertSame
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class UnityPackagePhaseTest extends BasePipelineTest {
    private def currentBuild

    @BeforeEach
    void configurePipelineTest() {
        super.setUp()
        binding.setVariable('env', [BRANCH_NAME: 'main', JENKINS_UNITY_ENV: 'EXISTING'])
        binding.setVariable('WORKSPACE', 'C:/workspace')
        currentBuild = new Expando(currentResult: 'SUCCESS', result: null)
        binding.setVariable('currentBuild', currentBuild)
        helper.registerAllowedMethod('dir', [String, Closure]) { String ignored, Closure body -> body() }
        helper.registerAllowedMethod('deleteDir', []) {}
        helper.registerAllowedMethod('echo', [String]) {}
        helper.registerAllowedMethod('unstash', [String]) {}
    }

    @Test
    void preparesMetadataOnceWithoutAllocatingANode() {
        int metadataReads = 0
        def stashes = []
        helper.registerAllowedMethod('pwd', []) { 'C:/workspace' }
        helper.registerAllowedMethod('fileExists', [String]) { String path -> path.endsWith('/Package') }
        helper.registerAllowedMethod('readJSON', [Map]) { Map ignored ->
            metadataReads++
            [name: 'net.example.package', version: '1.2.3']
        }
        helper.registerAllowedMethod('stash', [Map]) { Map args -> stashes << args }

        def prepare = loadScript('vars/prepareUnityPackage.groovy')
        def prepared = prepare.call {
            PACKAGE_LOCATION = 'Package'
            PACKAGE_BRANCH = 'release'
            VALIDATE_CHANGELOG = false
            CHECK_FORMATTING = false
            RUN_UNITY_TESTS = false
        }

        assertEquals(1, metadataReads)
        assertEquals('net.example.package', prepared.context.packageId)
        assertEquals('1.2.3', prepared.context.version)
        assertEquals('release', prepared.context.branch)
        assertEquals(1, stashes.size())
        assertTrue(stashes[0].name.startsWith('unity-package-source-'))
        assertFalse(helper.callStack.any { it.methodName == 'node' })
    }

    @Test
    void separateTestInvocationsUseSeparateTemporaryDirectories() {
        def directories = []
        helper.registerAllowedMethod('pwd', [Map]) { Map ignored -> 'C:/workspace@tmp' }
        helper.registerAllowedMethod('dir', [String, Closure]) { String directory, Closure body ->
            directories << directory
            body()
        }

        def prepared = preparedPackage([
            CHECK_FORMATTING: false,
            RUN_UNITY_TESTS: false,
        ])
        def testPackage = loadScript('vars/testUnityPackage.groovy')
        testPackage.call(prepared)
        testPackage.call(prepared)

        def invocationDirectories = directories.findAll { it.startsWith('C:/workspace@tmp/unity-package-execution-') }.unique()
        assertEquals(2, invocationDirectories.size())
        assertNotEquals(invocationDirectories[0], invocationDirectories[1])
        assertFalse(helper.callStack.any { it.methodName == 'node' })
    }

    @Test
    void testInterruptionSetsResultAndPropagates() {
        helper.registerAllowedMethod('pwd', [Map]) { Map ignored -> 'C:/workspace@tmp' }
        helper.registerAllowedMethod('withCredentials', [List, Closure]) { List ignored, Closure body -> body() }
        helper.registerAllowedMethod('withEnv', [List, Closure]) { List ignored, Closure body -> body() }
        helper.registerAllowedMethod('withUnity', [Closure]) { Closure body -> body() }
        def interruption = new FlowInterruptedException(Result.ABORTED, true)
        helper.registerAllowedMethod('callUnity', [String, String]) { String ignored, String ignoredFile -> throw interruption }

        def testPackage = loadScript('vars/testUnityPackage.groovy')
        def thrown = assertThrows(FlowInterruptedException) {
            testPackage.call(preparedPackage([CHECK_FORMATTING: false]))
        }

        assertSame(interruption, thrown)
        assertEquals(Result.ABORTED, currentBuild.result)
        assertFalse(helper.callStack.any { it.methodName == 'node' })
    }

    @Test
    void publishesFromRestoredSourceOnTheCurrentNode() {
        def commands = []
        def statuses = [1, 0]
        helper.registerAllowedMethod('pwd', [Map]) { Map ignored -> 'C:/publish@tmp' }
        helper.registerAllowedMethod('execStatus', [String]) { String command ->
            commands << command
            statuses.remove(0)
        }

        def publish = loadScript('vars/publishUnityPackage.groovy')
        publish.call(preparedPackage([
            CHECK_FORMATTING: false,
            PUBLISH_TO_VERDACCIO: true,
        ]))

        assertEquals(2, commands.size())
        assertTrue(commands[0].startsWith("npm view 'net.example.package@1.2.3'"))
        assertTrue(commands[1].startsWith('npm publish .'))
        assertFalse(helper.callStack.any { it.methodName == 'node' })
    }

    @Test
    void refusesPublicationAfterAnUnstableMatrixResult() {
        currentBuild.currentResult = 'UNSTABLE'
        helper.registerAllowedMethod('pwd', [Map]) { Map ignored -> 'C:/publish@tmp' }
        helper.registerAllowedMethod('error', [String]) { String message -> throw new IllegalStateException(message) }

        def publish = loadScript('vars/publishUnityPackage.groovy')
        def failure = assertThrows(IllegalStateException) {
            publish.call(preparedPackage([
                CHECK_FORMATTING: false,
                PUBLISH_TO_VERDACCIO: true,
            ]))
        }

        assertTrue(failure.message.contains("Current result is 'UNSTABLE'"))
        assertFalse(helper.callStack.any { it.methodName == 'execStatus' })
    }

    @Test
    void usesNpmPackMetadataForTheDirectStorageFallback() {
        def statuses = [1, 1]
        def commands = []
        Map writtenJson
        helper.registerAllowedMethod('pwd', [Map]) { Map ignored -> 'C:/publish@tmp' }
        helper.registerAllowedMethod('execStatus', [String]) { String ignored -> statuses.remove(0) }
        helper.registerAllowedMethod('fileExists', [String]) { String path -> path == '/storage/net.example.package/package.json' }
        helper.registerAllowedMethod('execStdout', [String]) { String command ->
            if (command.startsWith('npm pack')) {
                return '[{"filename":"net.example.package-1.2.3.tgz","integrity":"sha512-value","shasum":"sha1-value"}]'
            }
            command.startsWith('node') ? 'v22.0.0\n' : '10.0.0\n'
        }
        helper.registerAllowedMethod('readJSON', [Map]) { Map arguments ->
            if (arguments.text) {
                return [[filename: 'net.example.package-1.2.3.tgz', integrity: 'sha512-value', shasum: 'sha1-value']]
            }
            if (arguments.file == 'package.json') {
                return [name: 'net.example.package', version: '1.2.3']
            }
            [versions: [:], time: [:], 'dist-tags': [:], _attachments: [:]]
        }
        helper.registerAllowedMethod('exec', [String]) { String command -> commands << command }
        helper.registerAllowedMethod('writeJSON', [Map]) { Map arguments -> writtenJson = arguments }

        def publish = loadScript('vars/publishUnityPackage.groovy')
        publish.call(preparedPackage([
            CHECK_FORMATTING: false,
            PUBLISH_TO_VERDACCIO: true,
            VERDACCIO_STORAGE: '/storage',
        ]))

        assertEquals(1, commands.size())
        assertTrue(commands[0].startsWith("mv 'C:/publish@tmp/unity-package-publish-execution-"))
        assertEquals('/storage/net.example.package/package.json', writtenJson.file.toString())
        assertEquals('sha512-value', writtenJson.json.versions['1.2.3'].dist.integrity)
        assertEquals('1.2.3', writtenJson.json['dist-tags'].latest)
    }

    @Test
    void reportsWithoutRequestingAWorkspace() {
        def reports = []
        currentBuild.resultIsWorseOrEqualTo = { String threshold -> threshold == 'FAILURE' }
        helper.registerAllowedMethod('reportToDiscord', [String, Object, String]) { String webhook, Object ignored, String name ->
            reports << [webhook, name]
        }

        def report = loadScript('vars/reportUnityPackage.groovy')
        report.call(preparedPackage([
            CHECK_FORMATTING: false,
            REPORT_TO_DISCORD: true,
            DISCORD_WEBHOOK: 'https://discord.example/webhook',
            DISCORD_THRESHOLD: 'FAILURE',
        ]))

        assertEquals([['https://discord.example/webhook', 'net.example.package v1.2.3']], reports)
        assertFalse(helper.callStack.any { it.methodName == 'pwd' || it.methodName == 'node' })
    }

    @Test
    void loadsTheTopLevelDeclarativeWrapper() {
        def wrapper = loadScript('vars/unityPackagePipeline.groovy')

        assertFalse(wrapper.metaClass.respondsTo(wrapper, 'call', Object).empty)
        assertFalse(wrapper.metaClass.respondsTo(wrapper, 'call').empty)
    }

    private PreparedUnityPackage preparedPackage(Map overrides = [:]) {
        def options = UnityPackageOptions.fromMap([
            VALIDATE_CHANGELOG: false,
            CHECK_FORMATTING: false,
        ] + overrides)
        def context = new UnityPackageContext('net.example.package', '1.2.3', 'main', '.')
        new PreparedUnityPackage(options, context, 'execution', 'package-stash', 'configuration-stash')
    }
}
