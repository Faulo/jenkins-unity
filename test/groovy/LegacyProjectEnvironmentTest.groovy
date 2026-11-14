import com.lesfurets.jenkins.unit.BasePipelineTest
import net.slothsoft.jenkins.unity.UnityProjectOptions
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class LegacyProjectEnvironmentTest extends BasePipelineTest {
    @Test
    void mapAndClosureFormsRunMetadataVersionAndBuildInsideUnity() {
        super.setUp()
        def environment = [BRANCH_NAME: 'main', WORKSPACE: '/workspace with spaces',
            JENKINS_UNITY_ENV: 'CUSTOM', UNITY_CREDENTIALS_USR: 'external-user']
        binding.setVariable('env', environment)
        binding.setVariable('WORKSPACE', environment.WORKSPACE)
        binding.setVariable('WORKSPACE_TMP', '/workspace@tmp')
        binding.setVariable('currentBuild', new Expando(result: null))
        boolean insideUnity = false
        def commands = []
        helper.registerAllowedMethod('withEnv', [List, Closure]) { List values, Closure body ->
            def previous = environment.JENKINS_UNITY_ENV
            environment.JENKINS_UNITY_ENV = values[0].substring('JENKINS_UNITY_ENV='.length())
            try { body() } finally { environment.JENKINS_UNITY_ENV = previous }
        }
        def unityEnvironment = loadScript('vars/withUnityProjectEnvironment.groovy')
        helper.registerAllowedMethod('withUnityProjectEnvironment', [UnityProjectOptions, Closure]) { UnityProjectOptions options, Closure body ->
            unityEnvironment.call(options, body)
        }
        helper.registerAllowedMethod('withUnity', [Closure]) { Closure body ->
            assertTrue(environment.JENKINS_UNITY_ENV.tokenize(':').containsAll([
                'CUSTOM', 'UNITY_CREDENTIALS_USR', 'UNITY_CREDENTIALS_PSW', 'EMAIL_CREDENTIALS_USR', 'EMAIL_CREDENTIALS_PSW']))
            insideUnity = true
            try { body() } finally { insideUnity = false }
        }
        helper.registerAllowedMethod('usernamePassword', [Map]) { Map value -> value }
        helper.registerAllowedMethod('withCredentials', [List, Closure]) { List ignored, Closure body -> body() }
        helper.registerAllowedMethod('stage', [String, Closure]) { String ignored, Closure body -> body() }
        helper.registerAllowedMethod('dir', [String, Closure]) { String ignored, Closure body -> body() }
        helper.registerAllowedMethod('deleteDir', []) { }
        helper.registerAllowedMethod('fileExists', [String]) { String ignored -> true }
        helper.registerAllowedMethod('junit', [Map]) { Map ignored -> }
        helper.registerAllowedMethod('zip', [Map]) { Map ignored -> }
        helper.registerAllowedMethod('callComposer', [String]) { String command ->
            assertTrue(insideUnity)
            commands << command
            command.contains('autoversion') ? '1.2.3' : 'Game'
        }
        helper.registerAllowedMethod('callUnity', [String, String]) { String command, String report ->
            assertTrue(insideUnity)
            commands << command
            ''
        }
        def step = loadScript('vars/unityProject.groovy')
        helper.registerAllowedMethod('unityProject', [Map]) { Map args -> step.call(args) }
        for (boolean closureForm : [false, true]) {
            def options = [AUTOVERSION: 'git', BUILD_FOR_LINUX: '1', UNITY_CREDENTIALS: 'unity', EMAIL_CREDENTIALS: 'email']
            if (closureForm) {
                step.call { options.each { key, value -> delegate[key] = value } }
            } else {
                step.call(options)
            }
            assertFalse(insideUnity)
            assertEquals('CUSTOM', environment.JENKINS_UNITY_ENV)
            assertNull(binding.getVariable('currentBuild').result)
        }
        assertEquals(2, commands.count { it == "exec autoversion 'git' '/workspace with spaces'" })
        assertEquals(2, commands.count { it.startsWith('build ') })
    }
}
