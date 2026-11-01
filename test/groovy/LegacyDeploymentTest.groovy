import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class LegacyDeploymentTest extends BasePipelineTest {
    @Test
    void mapAndClosureFormsUseCiToolsForBothDeployments() {
        super.setUp()
        binding.setVariable('env', [BRANCH_NAME: 'main'])
        binding.setVariable('WORKSPACE', '/workspace')
        binding.setVariable('WORKSPACE_TMP', '/workspace@tmp')
        binding.setVariable('currentBuild', new Expando(currentResult: 'SUCCESS', result: null))
        def commands = []
        def containers = []
        def credentialScopes = []
        binding.setVariable('docker', new Expando(image: { String name ->
            assertEquals('faulo/ci-tools', name)
            new Expando(inside: { String arguments, Closure body -> containers << arguments; body() })
        }))
        helper.registerAllowedMethod('isUnix', []) { true }
        helper.registerAllowedMethod('pwd', []) { '/workspace@tmp/reports' }
        helper.registerAllowedMethod('stage', [String, Closure]) { String ignored, Closure body -> body() }
        helper.registerAllowedMethod('dir', [String, Closure]) { String ignored, Closure body -> body() }
        helper.registerAllowedMethod('withCredentials', [List, Closure]) { List values, Closure body -> credentialScopes << values*.credentialsId; body() }
        helper.registerAllowedMethod('usernamePassword', [Map]) { Map args -> args }
        helper.registerAllowedMethod('string', [Map]) { Map args -> args }
        helper.registerAllowedMethod('deleteDir', []) { }
        helper.registerAllowedMethod('zip', [Map]) { Map ignored -> }
        helper.registerAllowedMethod('fileExists', [String]) { String ignored -> false }
        helper.registerAllowedMethod('junit', [Map]) { Map ignored -> }
        helper.registerAllowedMethod('writeFile', [Map]) { Map args -> assertEquals('generated VDF', args.text) }
        helper.registerAllowedMethod('callComposer', [String]) { String ignored -> 'Game' }
        helper.registerAllowedMethod('callUnity', [String, String]) { String ignored, String report -> '' }
        helper.registerAllowedMethod('sh', [Map]) { Map args -> commands << args.script.toString(); args.returnStdout ? 'generated VDF' : null }
        def step = loadScript('vars/unityProject.groovy')
        helper.registerAllowedMethod('unityProject', [Map]) { Map args -> step.call(args) }
        def options = [TEST_FORMATTING: '0', TEST_UNITY: '0', BUILD_DOCUMENTATION: '0', BUILD_FOR_WINDOWS: '1',
            DEPLOYMENT_BRANCHES: ['main'], DEPLOY_TO_STEAM: '1', STEAM_ID: '1234', STEAM_DEPOT_WINDOWS: '11', STEAM_CREDENTIALS: 'steam',
            DEPLOY_TO_ITCH: '1', ITCH_ID: 'author/game', ITCH_CREDENTIALS: 'itch']
        step.call(options)
        step.call { options.each { key, value -> delegate[key] = value } }
        assertEquals(4, containers.size())
        assertEquals([[], ['steam'], ['itch'], [], ['steam'], ['itch']], credentialScopes)
        assertEquals(2, commands.count { it.contains('steamcmd +login') })
        assertEquals(2, commands.count { it.contains("butler push --if-changed 'build-windows' 'author/game:windows-x64'") })
        assertEquals(null, binding.getVariable('currentBuild').result)
    }
}
