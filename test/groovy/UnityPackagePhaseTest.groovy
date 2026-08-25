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
    void preparationOnlyCreatesPortablePackageData() {
        int metadataReads = 0
        def stashes = []
        helper.registerAllowedMethod('pwd', []) { 'C:/workspace' }
        helper.registerAllowedMethod('readJSON', [Map]) { Map ignored ->
            metadataReads++
            [name: 'net.example.package', version: '1.2.3']
        }
        helper.registerAllowedMethod('stash', [Map]) { Map args -> stashes << args }

        def prepare = loadScript('vars/prepareUnityPackage.groovy')
        def prepared = prepare.call {
            PACKAGE_LOCATION = 'Package'
            PACKAGE_BRANCH = 'release'
            TEST_CHANGELOG = true
            TEST_FORMATTING = false
            TEST_UNITY = false
        }

        assertEquals(1, metadataReads)
        assertEquals('net.example.package', prepared.context.packageId)
        assertEquals('1.2.3', prepared.context.version)
        assertEquals('release', prepared.context.branch)
        assertEquals(1, stashes.size())
        assertTrue(stashes[0].name.startsWith('unity-package-source-'))
        assertFalse(helper.callStack.any { it.methodName in ['stage', 'node', 'fileExists', 'unstable', 'error'] })
    }

    @Test
    void separateTestInvocationsUseSeparateTemporaryDirectories() {
        def directories = []
        helper.registerAllowedMethod('pwd', [Map]) { Map ignored -> 'C:/workspace@tmp' }
        helper.registerAllowedMethod('dir', [String, Closure]) { String directory, Closure body ->
            directories << directory
            body()
        }

        helper.registerAllowedMethod('fileExists', [String]) { String ignored -> true }
        helper.registerAllowedMethod('readFile', [String]) { String ignored -> '## [1.2.3] - 2026-08-25' }
        def prepared = preparedPackage([TEST_CHANGELOG: true])
        def testPackage = loadScript('vars/testUnityPackage.groovy')
        testPackage.call(prepared, 'changelog')
        testPackage.call(prepared, 'changelog')

        def invocationDirectories = directories.findAll {
            it.startsWith('C:/workspace@tmp/unity-package-execution-') && !it.endsWith('/package')
        }.unique()
        assertEquals(2, invocationDirectories.size())
        assertNotEquals(invocationDirectories[0], invocationDirectories[1])
        assertFalse(helper.callStack.any { it.methodName == 'node' })
    }

    @Test
    void packageTestOperationsDoNotAllocateStages() {
        def stages = []
        def documentation = []
        helper.registerAllowedMethod('pwd', [Map]) { Map ignored -> 'C:/workspace@tmp' }
        helper.registerAllowedMethod('stage', [String, Closure]) { String name, Closure body ->
            stages << name
            body()
        }
        helper.registerAllowedMethod('withCredentials', [List, Closure]) { List ignored, Closure body -> body() }
        helper.registerAllowedMethod('withEnv', [List, Closure]) { List ignored, Closure body -> body() }
        helper.registerAllowedMethod('withUnity', [Closure]) { Closure body -> body() }
        helper.registerAllowedMethod('callUnity', [String, String]) { String ignored, String ignoredFile -> }
        helper.registerAllowedMethod('callUnity', [String]) { String command -> documentation << command }
        helper.registerAllowedMethod('junit', [Map]) { Map ignored -> }
        helper.registerAllowedMethod('callDotnetFormat', [String, String, String]) { String ignoredSolution, String ignoredReports, String ignoredExclusions -> }
        helper.registerAllowedMethod('callDocFX', [String]) { String reportName -> documentation << reportName }
        helper.registerAllowedMethod('catchError', [Map, Closure]) { Map ignored, Closure body -> body() }
        helper.registerAllowedMethod('fileExists', [String]) { String ignored -> true }

        def testPackage = loadScript('vars/testUnityPackage.groovy')
        def prepared = preparedPackage([
            TEST_FORMATTING: true,
            TEST_UNITY: true,
            UNITY_TEST_MODES: ['EditMode', 'PlayMode'],
            BUILD_DOCUMENTATION: true,
        ])
        testPackage.call(prepared, 'formatting')
        testPackage.call(prepared, 'documentation')
        testPackage.call(prepared, 'unity')

        assertTrue(stages.empty)
        assertTrue(documentation[0].startsWith("unity-documentation 'C:/workspace@tmp/unity-package-execution-"))
        assertEquals('net.example.package', documentation[1])
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
            testPackage.call(preparedPackage([TEST_FORMATTING: false]))
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
            TEST_FORMATTING: false,
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
                TEST_FORMATTING: false,
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
            TEST_FORMATTING: false,
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
            TEST_FORMATTING: false,
            REPORT_TO_DISCORD: true,
            DISCORD_WEBHOOK: 'https://discord.example/webhook',
            DISCORD_THRESHOLD: 'FAILURE',
        ]))

        assertEquals([['https://discord.example/webhook', 'net.example.package v1.2.3']], reports)
        assertFalse(helper.callStack.any { it.methodName == 'pwd' || it.methodName == 'node' })
    }

    @Test
    void orchestratesConfiguredAgentsInDeclarationOrder() {
        def stages = []
        def nodes = []
        def events = []
        def images = []
        def tested = []
        def published = []
        def reported = []
        def prepared = preparedPackage([
            TEST_CHANGELOG: true,
            TEST_FORMATTING: true,
            TEST_UNITY: true,
            UNITY_TEST_MODES: ['EditMode'],
            BUILD_DOCUMENTATION: true,
            PUBLISH_TO_VERDACCIO: true,
            REPORT_TO_DISCORD: true,
            DISCORD_WEBHOOK: 'https://discord.example/webhook',
            REPORT_TO_OFFICE_365: true,
            OFFICE_365_WEBHOOK: 'https://office.example/webhook',
            REPORT_TO_ADAPTIVE_CARDS: true,
            ADAPTIVE_CARDS_WEBHOOK: 'https://cards.example/webhook',
        ])
        currentBuild.resultIsWorseOrEqualTo = { String ignored -> true }
        binding.setVariable('scm', new Expando())
        binding.setVariable('docker', new Expando(image: { String imageName ->
            images << imageName
            new Expando(inside: { String ignored, Closure body -> body() })
        }))
        helper.registerAllowedMethod('stage', [String, Closure]) { String name, Closure body ->
            stages << name
            events << "stage:${name}".toString()
            body()
        }
        helper.registerAllowedMethod('node', [String, Closure]) { String label, Closure body ->
            nodes << label
            events << "node:${label}".toString()
            body()
        }
        helper.registerAllowedMethod('checkout', [Object]) { Object ignored -> }
        helper.registerAllowedMethod('readJSON', [Map]) { Map ignored -> [name: 'net.example.package'] }
        helper.registerAllowedMethod('parallel', [Map]) { Map ignored ->
            throw new AssertionError('parallel must not be called')
        }
        helper.registerAllowedMethod('withEnv', [List, Closure]) { List ignored, Closure body -> body() }
        helper.registerAllowedMethod('prepareUnityPackage', [UnityPackageOptions]) { UnityPackageOptions ignored ->
            prepared
        }
        helper.registerAllowedMethod('testUnityPackage', [PreparedUnityPackage, String]) { PreparedUnityPackage value, String operation ->
            tested << [value, operation, nodes.last()]
        }
        helper.registerAllowedMethod('publishUnityPackage', [PreparedUnityPackage]) { PreparedUnityPackage value -> published << value }
        helper.registerAllowedMethod('reportUnityPackage', [PreparedUnityPackage, String]) { PreparedUnityPackage value, String method -> reported << [value, method] }

        def wrapper = loadScript('vars/unityPackagePipeline.groovy')
        wrapper.call([
            PREPARE_AGENT: 'prepare-node',
            PREPARE_IMAGE: 'prepare-image',
            PUBLISH_AGENT: 'publish-node',
            PUBLISH_IMAGE: 'publish-image',
            UNITY_AGENTS: [Windows: 'windows-node', Linux: 'linux-node', WebGL: 'webgl-node'],
            PUBLISH_TO_VERDACCIO: true,
        ])

        assertEquals([
            'Package: net.example.package',
            'Agent: Windows',
            'Test: CHANGELOG.md',
            'Test: .editorconfig',
            'Build: DocFX documentation',
            'Test: Unity (EditMode)',
            'Agent: Linux',
            'Test: Unity (EditMode)',
            'Agent: WebGL',
            'Test: Unity (EditMode)',
            'Publish: Verdaccio',
            'Report: Discord',
            'Report: Office 365',
            'Report: Adaptive Cards',
        ], stages)
        assertEquals(['prepare-node', 'windows-node', 'linux-node', 'webgl-node', 'publish-node'], nodes)
        assertEquals([
            'node:prepare-node',
            'stage:Package: net.example.package',
            'stage:Agent: Windows',
            'node:windows-node',
            'stage:Test: CHANGELOG.md',
            'stage:Test: .editorconfig',
            'stage:Build: DocFX documentation',
            'stage:Test: Unity (EditMode)',
            'stage:Agent: Linux',
            'node:linux-node',
            'stage:Test: Unity (EditMode)',
            'stage:Agent: WebGL',
            'node:webgl-node',
            'stage:Test: Unity (EditMode)',
            'stage:Publish: Verdaccio',
            'node:publish-node',
            'stage:Report: Discord',
            'stage:Report: Office 365',
            'stage:Report: Adaptive Cards',
        ], events)
        assertEquals(['prepare-image', 'publish-image'], images)
        assertEquals([
            [prepared, 'changelog', 'windows-node'],
            [prepared, 'formatting', 'windows-node'],
            [prepared, 'documentation', 'windows-node'],
            [prepared, 'unity', 'windows-node'],
            [prepared, 'unity', 'linux-node'],
            [prepared, 'unity', 'webgl-node'],
        ], tested)
        assertEquals([prepared], published)
        assertEquals([
            [prepared, 'discord'],
            [prepared, 'office365'],
            [prepared, 'adaptiveCards'],
        ], reported)
    }

    @Test
    void skipsDisabledTestAndPublishStages() {
        def stages = []
        def nodes = []
        def published = []
        def tested = []
        def prepared = preparedPackage([
            TEST_FORMATTING: false,
            TEST_UNITY: false,
            REPORT_TO_DISCORD: true,
            DISCORD_WEBHOOK: 'https://discord.example/webhook',
            DISCORD_THRESHOLD: 'FAILURE',
        ])
        currentBuild.resultIsWorseOrEqualTo = { String ignored -> false }
        binding.setVariable('scm', new Expando())
        binding.setVariable('docker', new Expando(image: { String ignored ->
            new Expando(inside: { String ignoredArgs, Closure body -> body() })
        }))
        helper.registerAllowedMethod('stage', [String, Closure]) { String name, Closure body ->
            stages << name
            body()
        }
        helper.registerAllowedMethod('node', [String, Closure]) { String label, Closure body ->
            nodes << label
            body()
        }
        helper.registerAllowedMethod('checkout', [Object]) { Object ignored -> }
        helper.registerAllowedMethod('readJSON', [Map]) { Map ignored -> [name: 'net.example.package'] }
        helper.registerAllowedMethod('parallel', [Map]) { Map ignored ->
            throw new AssertionError('parallel must not be called')
        }
        helper.registerAllowedMethod('prepareUnityPackage', [UnityPackageOptions]) { UnityPackageOptions ignored ->
            prepared
        }
        helper.registerAllowedMethod('testUnityPackage', [PreparedUnityPackage, String]) { PreparedUnityPackage value, String ignored -> tested << value }
        helper.registerAllowedMethod('publishUnityPackage', [PreparedUnityPackage]) { PreparedUnityPackage value -> published << value }
        helper.registerAllowedMethod('reportUnityPackage', [PreparedUnityPackage, String]) { PreparedUnityPackage ignored, String ignoredMethod -> }

        def wrapper = loadScript('vars/unityPackagePipeline.groovy')
        wrapper.call([
            PREPARE_AGENT: 'prepare-node',
            PREPARE_IMAGE: 'prepare-image',
            PUBLISH_AGENT: 'publish-node',
            PUBLISH_IMAGE: 'publish-image',
            UNITY_AGENTS: [:],
        ])

        assertEquals(['Package: net.example.package'], stages)
        assertEquals(['prepare-node'], nodes)
        assertTrue(tested.empty)
        assertTrue(published.empty)
    }

    private PreparedUnityPackage preparedPackage(Map overrides = [:]) {
        def options = UnityPackageOptions.fromMap([
            TEST_CHANGELOG: false,
            TEST_FORMATTING: false,
        ] + overrides)
        def context = new UnityPackageContext('net.example.package', '1.2.3', 'main', '.')
        new PreparedUnityPackage(options, context, 'execution', 'package-stash', 'configuration-stash')
    }
}
