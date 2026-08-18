import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

class PipelineStepDelegationTest extends BasePipelineTest {
    @BeforeEach
    void configurePipelineTest() {
        super.setUp()
    }

    @Test
    void delegatesCallShellToExec() {
        def invocations = []
        helper.registerAllowedMethod('exec', [Map]) { Map arguments ->
            invocations.add(arguments)
        }

        def callShell = loadScript('vars/callShell.groovy')
        callShell.call('first command')
        callShell.call('second command', true)

        assertEquals([
            [script: 'first command', echoScript: false],
            [script: 'second command', echoScript: true]
        ], invocations)
    }

    @Test
    void delegatesCallShellStatusToExecStatus() {
        def invocation
        helper.registerAllowedMethod('execStatus', [Map]) { Map arguments ->
            invocation = arguments
            return 23
        }

        def callShellStatus = loadScript('vars/callShellStatus.groovy')
        def result = callShellStatus.call('status command', true)

        assertEquals([script: 'status command', echoScript: true], invocation)
        assertEquals(23, result)
    }

    @Test
    void delegatesCallShellStdoutToExecStdout() {
        def invocation
        helper.registerAllowedMethod('execStdout', [Map]) { Map arguments ->
            invocation = arguments
            return 'captured output'
        }

        def callShellStdout = loadScript('vars/callShellStdout.groovy')
        def result = callShellStdout.call('stdout command')

        assertEquals([script: 'stdout command', echoScript: false], invocation)
        assertEquals('captured output', result)
    }

    @Test
    void delegatesWithUnityToInsideDockerContainer() {
        binding.setVariable('env', [
            JENKINS_UNITY_CONTAINER: 'unity-sidecar',
            JENKINS_UNITY_ENV: 'FIRST::SECOND:FIRST'
        ])

        def containerArguments
        def compatibilityEnvironment
        def bodyCalled = false
        helper.registerAllowedMethod('insideDockerContainer', [Map, Closure]) { Map arguments, Closure body ->
            containerArguments = arguments
            env.PIPELINE_DOCKER_CONTAINER_NAME = arguments.container
            env.PIPELINE_DOCKER_CONTAINER_ID = 'container-id'
            env.PIPELINE_DOCKER_CONTAINER_OS = 'linux'
            body()
        }
        helper.registerAllowedMethod('withEnv', [List, Closure]) { List environment, Closure body ->
            compatibilityEnvironment = environment
            body()
        }

        def withUnity = loadScript('vars/withUnity.groovy')
        withUnity.call {
            bodyCalled = true
        }

        assertEquals([
            container: 'unity-sidecar',
            environment: ['FIRST', 'SECOND']
        ], containerArguments)
        assertEquals([
            'JENKINS_UNITY_CONTAINER=unity-sidecar',
            'JENKINS_UNITY_CONTAINER_ID=container-id',
            'JENKINS_UNITY_CONTAINER_OS=linux'
        ], compatibilityEnvironment*.toString())
        assertTrue(bodyCalled)
    }

    @Test
    void delegatesExplicitWithUnityContainer() {
        binding.setVariable('env', [JENKINS_UNITY_CONTAINER: 'default-sidecar'])

        def containerArguments
        helper.registerAllowedMethod('insideDockerContainer', [Map, Closure]) { Map arguments, Closure body ->
            containerArguments = arguments
            env.PIPELINE_DOCKER_CONTAINER_NAME = arguments.container
            env.PIPELINE_DOCKER_CONTAINER_ID = 'explicit-container-id'
            env.PIPELINE_DOCKER_CONTAINER_OS = 'windows'
            body()
        }
        helper.registerAllowedMethod('withEnv', [List, Closure]) { List environment, Closure body ->
            body()
        }

        def withUnity = loadScript('vars/withUnity.groovy')
        withUnity.call('explicit-sidecar') {}

        assertEquals([
            container: 'explicit-sidecar',
            environment: []
        ], containerArguments)
    }
}
