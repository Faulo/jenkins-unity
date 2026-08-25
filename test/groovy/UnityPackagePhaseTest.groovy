import com.lesfurets.jenkins.unit.BasePipelineTest
import hudson.model.Result
import net.slothsoft.jenkins.unity.InstalledUnityPackage
import net.slothsoft.jenkins.unity.PreparedUnityPackage
import net.slothsoft.jenkins.unity.UnityPackageContext
import net.slothsoft.jenkins.unity.UnityPackageOptions
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
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
    void installsPackageIntoOneReusableUnityProject() {
        def directories = []
        def commands = []
        helper.registerAllowedMethod('pwd', [Map]) { Map ignored -> 'C:/workspace@tmp' }
        helper.registerAllowedMethod('dir', [String, Closure]) { String directory, Closure body ->
            directories << directory
            body()
        }
        helper.registerAllowedMethod('withUnityPackageEnvironment', [PreparedUnityPackage, Closure]) { PreparedUnityPackage ignored, Closure body -> body() }
        helper.registerAllowedMethod('callUnity', [String, String]) { String command, String report -> commands << [command, report] }
        helper.registerAllowedMethod('junit', [Map]) { Map ignored -> }
        helper.registerAllowedMethod('fileExists', [String]) { String ignored -> true }
        def install = loadScript('vars/installUnityPackage.groovy')
        def installed = install.call(preparedPackage([TEST_FORMATTING: false]))

        assertTrue(installed.workDirectory.startsWith('C:/workspace@tmp/unity-package-execution-'))
        assertEquals("${installed.workDirectory}/package".toString(), installed.packageDirectory)
        assertEquals("${installed.workDirectory}/project".toString(), installed.projectDirectory)
        assertEquals([["unity-package-install '${installed.packageDirectory}' '${installed.projectDirectory}'".toString(), 'package-install.xml']], commands)
        assertFalse(helper.callStack.any { it.methodName in ['stage', 'node'] })
    }

    @Test
    void packageAndProjectOperationsDoNotAllocateStages() {
        def stages = []
        def commands = []
        helper.registerAllowedMethod('stage', [String, Closure]) { String name, Closure body ->
            stages << name
            body()
        }
        helper.registerAllowedMethod('withUnityPackageEnvironment', [PreparedUnityPackage, Closure]) { PreparedUnityPackage ignored, Closure body -> body() }
        helper.registerAllowedMethod('callUnity', [String, String]) { String command, String report -> commands << [command, report] }
        helper.registerAllowedMethod('callUnity', [String]) { String command -> commands << command }
        helper.registerAllowedMethod('junit', [Map]) { Map ignored -> }
        helper.registerAllowedMethod('callDotnetFormat', [String, String, String]) { String solution, String reports, String exclusions -> commands << [solution, reports, exclusions] }
        helper.registerAllowedMethod('callDocFX', [String]) { String reportName -> commands << reportName }
        helper.registerAllowedMethod('catchError', [Map, Closure]) { Map ignored, Closure body -> body() }
        helper.registerAllowedMethod('fileExists', [String]) { String ignored -> true }
        helper.registerAllowedMethod('readFile', [String]) { String ignored -> '## [1.2.3] - 2026-08-25' }

        def installed = installedPackage([
            TEST_CHANGELOG: true,
            TEST_FORMATTING: true,
            TEST_UNITY: true,
            UNITY_TEST_MODES: ['EditMode', 'PlayMode'],
            BUILD_DOCUMENTATION: true,
        ])
        loadScript('vars/testUnityPackage.groovy').call(installed)
        def buildProject = loadScript('vars/buildUnityProject.groovy')
        buildProject.call(installed, 'solution')
        buildProject.call(installed, 'documentation')
        def testProject = loadScript('vars/testUnityProject.groovy')
        testProject.call(installed, 'formatting')
        testProject.call(installed, 'unity')

        assertTrue(stages.empty)
        assertTrue(commands.contains(["unity-method '${installed.projectDirectory}' Slothsoft.UnityExtensions.Editor.Build.Solution".toString(), 'build-solution.xml']))
        assertTrue(commands.contains("unity-documentation '${installed.projectDirectory}'".toString()))
        assertTrue(commands.contains('net.example.package'))
        assertTrue(commands.contains(["${installed.projectDirectory}/project.sln".toString(), installed.reportsDirectory, '']))
        assertTrue(commands.contains(["unity-tests '${installed.projectDirectory}' EditMode PlayMode".toString(), 'tests.xml']))
        assertFalse(helper.callStack.any { it.methodName == 'node' })
    }

    @Test
    void projectTestInterruptionSetsResultAndPropagates() {
        helper.registerAllowedMethod('withUnityPackageEnvironment', [PreparedUnityPackage, Closure]) { PreparedUnityPackage ignored, Closure body -> body() }
        def interruption = new FlowInterruptedException(Result.ABORTED, true)
        helper.registerAllowedMethod('callUnity', [String, String]) { String ignored, String ignoredFile -> throw interruption }

        def testProject = loadScript('vars/testUnityProject.groovy')
        def thrown = assertThrows(FlowInterruptedException) {
            testProject.call(installedPackage([TEST_UNITY: true]), 'unity')
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
        def installedOn = []
        def built = []
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
        helper.registerAllowedMethod('parallel', [Map]) { Map ignored ->
            throw new AssertionError('parallel must not be called')
        }
        helper.registerAllowedMethod('prepareUnityPackage', [UnityPackageOptions]) { UnityPackageOptions ignored ->
            prepared
        }
        helper.registerAllowedMethod('installUnityPackage', [PreparedUnityPackage]) { PreparedUnityPackage value ->
            def installed = installedPackage(value.options, nodes.last())
            installedOn << nodes.last()
            installed
        }
        helper.registerAllowedMethod('buildUnityProject', [InstalledUnityPackage, String]) { InstalledUnityPackage value, String operation -> built << [operation, value.workDirectory] }
        helper.registerAllowedMethod('testUnityPackage', [InstalledUnityPackage]) { InstalledUnityPackage value -> tested << ['package', value.workDirectory] }
        helper.registerAllowedMethod('testUnityProject', [InstalledUnityPackage, String]) { InstalledUnityPackage value, String operation -> tested << [operation, value.workDirectory] }
        helper.registerAllowedMethod('deleteUnityProject', [InstalledUnityPackage]) { InstalledUnityPackage ignored -> }
        helper.registerAllowedMethod('publishUnityPackage', [PreparedUnityPackage]) { PreparedUnityPackage value -> published << value }
        helper.registerAllowedMethod('reportUnityPackage', [PreparedUnityPackage, String]) { PreparedUnityPackage value, String method -> reported << [value, method] }

        def wrapper = loadScript('vars/unityPackagePipeline.groovy')
        wrapper.call([
            PREPARE_AGENT: 'prepare-node',
            PREPARE_IMAGE: 'prepare-image',
            PUBLISH_AGENT: 'publish-node',
            PUBLISH_IMAGE: 'publish-image',
            PACKAGE_ID: 'net.example.package',
            UNITY_AGENTS: [Windows: 'windows-node', Linux: 'linux-node', WebGL: 'webgl-node'],
            PUBLISH_TO_VERDACCIO: true,
        ])

        assertEquals([
            'Package: net.example.package',
            'Agent: Windows',
            'Build: Unity package',
            'Test: CHANGELOG.md',
            'Build: C# solution',
            'Test: .editorconfig',
            'Build: DocFX documentation',
            'Test: Unity (EditMode)',
            'Agent: Linux',
            'Build: Unity package',
            'Test: Unity (EditMode)',
            'Agent: WebGL',
            'Build: Unity package',
            'Test: Unity (EditMode)',
            'Publish: Verdaccio',
            'Report: Discord',
            'Report: Office 365',
            'Report: Adaptive Cards',
        ], stages)
        assertEquals(['prepare-node', 'windows-node', 'linux-node', 'webgl-node', 'publish-node'], nodes)
        assertEquals([
            'stage:Package: net.example.package',
            'node:prepare-node',
            'stage:Agent: Windows',
            'node:windows-node',
            'stage:Build: Unity package',
            'stage:Test: CHANGELOG.md',
            'stage:Build: C# solution',
            'stage:Test: .editorconfig',
            'stage:Build: DocFX documentation',
            'stage:Test: Unity (EditMode)',
            'stage:Agent: Linux',
            'node:linux-node',
            'stage:Build: Unity package',
            'stage:Test: Unity (EditMode)',
            'stage:Agent: WebGL',
            'node:webgl-node',
            'stage:Build: Unity package',
            'stage:Test: Unity (EditMode)',
            'stage:Publish: Verdaccio',
            'node:publish-node',
            'stage:Report: Discord',
            'stage:Report: Office 365',
            'stage:Report: Adaptive Cards',
        ], events)
        assertEquals(['prepare-image', 'publish-image'], images)
        assertEquals(['windows-node', 'linux-node', 'webgl-node'], installedOn)
        assertEquals([
            ['solution', 'C:/windows-node'],
            ['documentation', 'C:/windows-node'],
        ], built)
        assertEquals([
            ['package', 'C:/windows-node'],
            ['formatting', 'C:/windows-node'],
            ['unity', 'C:/windows-node'],
            ['unity', 'C:/linux-node'],
            ['unity', 'C:/webgl-node'],
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
        helper.registerAllowedMethod('parallel', [Map]) { Map ignored ->
            throw new AssertionError('parallel must not be called')
        }
        helper.registerAllowedMethod('prepareUnityPackage', [UnityPackageOptions]) { UnityPackageOptions ignored ->
            prepared
        }
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

        assertEquals(['Package: Unity package'], stages)
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

    private InstalledUnityPackage installedPackage(Map overrides = [:]) {
        installedPackage(preparedPackage(overrides).options, 'agent')
    }

    private InstalledUnityPackage installedPackage(UnityPackageOptions options, String agent) {
        def context = new UnityPackageContext('net.example.package', '1.2.3', 'main', '.')
        def prepared = new PreparedUnityPackage(options, context, 'execution', 'package-stash', 'configuration-stash')
        new InstalledUnityPackage(prepared, "C:/${agent}", "C:/${agent}/package", "C:/${agent}/project", "C:/${agent}/reports")
    }
}
