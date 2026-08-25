import com.lesfurets.jenkins.unit.BasePipelineTest
import net.slothsoft.jenkins.unity.UnityProjectContext
import net.slothsoft.jenkins.unity.UnityProjectOptions
import net.slothsoft.jenkins.unity.UnityProjectPipelineOptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class UnityProjectPipelineTest extends BasePipelineTest {
    private def currentBuild

    @BeforeEach
    void configurePipelineTest() {
        super.setUp()
        binding.setVariable('env', [BRANCH_NAME: 'main', BUILD_NUMBER: '42', JENKINS_UNITY_ENV: 'EXISTING'])
        currentBuild = new Expando(currentResult: 'SUCCESS', result: null)
        currentBuild.resultIsWorseOrEqualTo = { String ignored -> true }
        binding.setVariable('currentBuild', currentBuild)
        binding.setVariable('scm', new Expando())
        helper.registerAllowedMethod('dir', [String, Closure]) { String ignored, Closure body -> body() }
        helper.registerAllowedMethod('deleteDir', []) { }
    }

    @Test
    void optionsAreStrictAndUseOneUnityAgent() {
        def options = UnityProjectPipelineOptions.fromMap([:])

        assertEquals('compose-unity', options.unityAgent)
        assertTrue(options.projectOptions.testFormatting)
        assertTrue(options.projectOptions.testUnity)
        assertFalse(options.projectOptions.deployToSteam)
        assertThrows(IllegalArgumentException) {
            UnityProjectPipelineOptions.fromMap([TEST_UNITY: '1'])
        }
        assertThrows(IllegalArgumentException) {
            UnityProjectPipelineOptions.fromMap([UNITY_AGENTS: [:]])
        }
    }

    @Test
    void orchestratesProjectLifecycleOnOneAgent() {
        def stages = []
        def events = []
        def nodes = []
        def built = []
        def tested = []
        def deployed = []
        def reported = []
        def versioned = []

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
        helper.registerAllowedMethod('pwd', []) { 'C:/workspace' }
        helper.registerAllowedMethod('pwd', [Map]) { Map ignored -> 'C:/workspace@tmp' }
        helper.registerAllowedMethod('withUnityProjectEnvironment', [UnityProjectOptions, Closure]) { UnityProjectOptions ignored, Closure body -> body() }
        helper.registerAllowedMethod('setUnityProjectVersion', [UnityProjectContext]) { UnityProjectContext project -> versioned << project }
        helper.registerAllowedMethod('buildUnityProject', [UnityProjectContext, String]) { UnityProjectContext project, String operation -> built << [project, operation] }
        helper.registerAllowedMethod('testUnityProject', [UnityProjectContext, String]) { UnityProjectContext project, String operation -> tested << [project, operation] }
        helper.registerAllowedMethod('deployUnityProject', [UnityProjectContext, String]) { UnityProjectContext project, String method -> deployed << [project, method] }
        helper.registerAllowedMethod('reportUnityProject', [UnityProjectContext, String]) { UnityProjectContext project, String method -> reported << [project, method] }
        helper.registerAllowedMethod('parallel', [Map]) { Map ignored -> throw new AssertionError('parallel must not be called') }

        def pipeline = loadScript('vars/unityProjectPipeline.groovy')
        pipeline.call([
            UNITY_AGENT: 'project-node',
            PROJECT_LOCATION: 'Game',
            PROJECT_ID: 'Example Game',
            PROJECT_VERSION: '1.2.3',
            PROJECT_BRANCH: 'main',
            TEST_FORMATTING: true,
            FORMATTING_LOCATION: 'Game/.editorconfig',
            TEST_UNITY: true,
            UNITY_TEST_MODES: ['EditMode'],
            BUILD_DOCUMENTATION: true,
            BUILD_FOR_WINDOWS: true,
            BUILD_FOR_LINUX: true,
            BUILD_FOR_MAC: true,
            BUILD_FOR_WEBGL: true,
            BUILD_FOR_ANDROID: true,
            DEPLOY_TO_STEAM: true,
            STEAM_CREDENTIALS: 'steam-credentials',
            STEAM_ID: '1234',
            STEAM_DEPOT_WINDOWS: '11',
            DEPLOY_TO_ITCH: true,
            ITCH_CREDENTIALS: 'itch-credentials',
            ITCH_ID: 'slothsoft/example',
            REPORT_TO_DISCORD: true,
            DISCORD_WEBHOOK: 'https://discord.example',
            REPORT_TO_OFFICE_365: true,
            OFFICE_365_WEBHOOK: 'https://office.example',
            REPORT_TO_ADAPTIVE_CARDS: true,
            ADAPTIVE_CARDS_WEBHOOK: 'https://cards.example',
        ])

        assertEquals([
            'Project: Example Game',
            'Set: Project version',
            'Build: C# solution',
            'Build: DocFX documentation',
            'Test: .editorconfig',
            'Test: Unity (EditMode)',
            'Build: Windows',
            'Build: Linux',
            'Build: macOS',
            'Build: WebGL',
            'Build: Android',
            'Deploy: Steam',
            'Deploy: itch.io',
            'Report: Discord',
            'Report: Office 365',
            'Report: Adaptive Cards',
        ], stages)
        assertEquals(['project-node'], nodes)
        assertEquals('stage:Project: Example Game', events[0])
        assertEquals('node:project-node', events[1])
        assertEquals('stage:Report: Discord', events[-3])

        assertEquals(1, versioned.size())
        def project = versioned[0]
        assertEquals('C:/workspace/Game', project.projectDirectory)
        assertEquals('C:/workspace@tmp/unity-project-reports', project.reportsDirectory)
        assertTrue((built*.last()) == ['solution', 'documentation', 'windows', 'linux', 'mac', 'webgl', 'android'])
        assertTrue((tested*.last()) == ['formatting', 'unity'])
        assertTrue((deployed*.last()) == ['steam', 'itch'])
        assertTrue((reported*.last()) == ['discord', 'office365', 'adaptiveCards'])
    }

    @Test
    void projectOperationsDoNotAllocateStagesOrNodes() {
        def stages = []
        def commands = []
        helper.registerAllowedMethod('stage', [String, Closure]) { String name, Closure body -> stages << name; body() }
        helper.registerAllowedMethod('withUnityProjectEnvironment', [UnityProjectOptions, Closure]) { UnityProjectOptions ignored, Closure body -> body() }
        helper.registerAllowedMethod('callUnity', [String, String]) { String command, String report -> commands << [command, report] }
        helper.registerAllowedMethod('callUnity', [String]) { String command -> commands << command }
        helper.registerAllowedMethod('junit', [Map]) { Map ignored -> }
        helper.registerAllowedMethod('catchError', [Map, Closure]) { Map ignored, Closure body -> body() }
        helper.registerAllowedMethod('callDocFX', [String]) { String name -> commands << name }
        helper.registerAllowedMethod('fileExists', [String]) { String ignored -> true }
        helper.registerAllowedMethod('readFile', [String]) { String ignored -> 'root = true' }
        helper.registerAllowedMethod('writeFile', [Map]) { Map ignored -> }
        helper.registerAllowedMethod('findFiles', [Map]) { Map ignored -> [new Expando(path: 'Game.sln')] as Object[] }
        helper.registerAllowedMethod('callDotnetFormat', [String, String, String]) { String solution, String reports, String exclusions -> commands << [solution, reports, exclusions] }

        def project = projectContext([
            TEST_FORMATTING: true,
            TEST_UNITY: true,
            UNITY_TEST_MODES: ['EditMode'],
            BUILD_DOCUMENTATION: true,
        ])
        def build = loadScript('vars/buildUnityProject.groovy')
        build.call(project, 'solution')
        build.call(project, 'documentation')
        def test = loadScript('vars/testUnityProject.groovy')
        test.call(project, 'formatting')
        test.call(project, 'unity')

        assertTrue(stages.empty)
        assertFalse(helper.callStack.any { it.methodName == 'node' })
        assertTrue(commands.contains(["unity-method 'C:/workspace/Game' Slothsoft.UnityExtensions.Editor.Build.Solution", 'build-solution.xml']))
        assertTrue(commands.contains("unity-documentation 'C:/workspace/Game'"))
        assertTrue(commands.contains(['C:/workspace/Game/Game.sln', 'C:/workspace@tmp/reports', 'Library']))
        assertTrue(commands.contains(["unity-tests 'C:/workspace/Game' EditMode", 'tests.xml']))
    }

    @Test
    void deploymentOperationsAreStageFreeAndDoNotPublishInTests() {
        def commands = []
        def credentials = []
        helper.registerAllowedMethod('usernamePassword', [Map]) { Map args -> args }
        helper.registerAllowedMethod('string', [Map]) { Map args -> args }
        helper.registerAllowedMethod('withCredentials', [List, Closure]) { List values, Closure body -> credentials.addAll(values); body() }
        helper.registerAllowedMethod('withEnv', [List, Closure]) { List ignored, Closure body -> body() }
        helper.registerAllowedMethod('withUnityProjectEnvironment', [UnityProjectOptions, Closure]) { UnityProjectOptions ignored, Closure body -> body() }
        helper.registerAllowedMethod('callUnity', [String]) { String command -> commands << command }
        helper.registerAllowedMethod('callUnity', [String, String]) { String command, String report -> commands << [command, report] }
        helper.registerAllowedMethod('callShell', [String]) { String command -> commands << command }

        def project = projectContext([
            TEST_FORMATTING: false,
            TEST_UNITY: false,
            BUILD_FOR_WINDOWS: true,
            BUILD_FOR_LINUX: true,
            DEPLOY_TO_STEAM: true,
            STEAM_CREDENTIALS: 'steam-credentials',
            STEAM_ID: '1234',
            STEAM_DEPOT_WINDOWS: '11',
            STEAM_DEPOT_LINUX: '12',
            DEPLOY_TO_ITCH: true,
            ITCH_CREDENTIALS: 'itch-credentials',
            ITCH_ID: 'slothsoft/example',
        ])
        def deploy = loadScript('vars/deployUnityProject.groovy')
        deploy.call(project, 'steam')
        deploy.call(project, 'itch')

        assertEquals(['steam-credentials', 'itch-credentials'], credentials*.credentialsId)
        assertTrue(commands.contains('steam-login'))
        assertTrue(commands.any { it instanceof List && it[0].contains('11=build-windows 12=build-linux') })
        assertTrue(commands.contains("butler push --if-changed 'build-windows' 'slothsoft/example:windows-x64'"))
        assertTrue(commands.contains("butler push --if-changed 'build-linux' 'slothsoft/example:linux-x64'"))
        assertFalse(helper.callStack.any { it.methodName in ['stage', 'node'] })
    }

    @Test
    void reportingUsesOnlyProjectMetadataAndCurrentBuild() {
        def reports = []
        currentBuild.resultIsWorseOrEqualTo = { String threshold -> threshold == 'FAILURE' }
        helper.registerAllowedMethod('reportToDiscord', [String, Object, String]) { String webhook, Object ignored, String name -> reports << [webhook, name] }

        def project = projectContext([
            REPORT_TO_DISCORD: true,
            DISCORD_WEBHOOK: 'https://discord.example',
            DISCORD_THRESHOLD: 'FAILURE',
        ])
        loadScript('vars/reportUnityProject.groovy').call(project)

        assertEquals([['https://discord.example', 'Example Game v1.2.3']], reports)
        assertFalse(helper.callStack.any { it.methodName in ['pwd', 'node', 'stage'] })
    }

    private UnityProjectContext projectContext(Map overrides = [:]) {
        def options = UnityProjectOptions.fromMap([
            PROJECT_LOCATION: 'Game',
            PROJECT_BRANCH: 'main',
        ] + overrides)
        new UnityProjectContext(options, 'Example Game', '1.2.3', 'main', 'C:/workspace', 'C:/workspace/Game', 'C:/workspace@tmp/reports')
    }
}
