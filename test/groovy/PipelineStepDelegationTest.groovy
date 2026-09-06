import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertThrows
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
            JENKINS_UNITY_CONTAINER_LABEL: 'net.slothsoft.role=compose-unity',
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
        assertFalse(helper.callStack.any { it.methodName in ['isWindows', 'powershell', 'sh'] })
    }

    @Test
    void delegatesExplicitWithUnityContainer() {
        binding.setVariable('env', [
            JENKINS_UNITY_CONTAINER: 'default-sidecar',
            JENKINS_UNITY_CONTAINER_LABEL: 'net.slothsoft.role=compose-unity'
        ])

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
        assertFalse(helper.callStack.any { it.methodName in ['isWindows', 'powershell', 'sh'] })
    }

    @Test
    void discoversFirstUnityContainerByLabelOnLinux() {
        binding.setVariable('env', [JENKINS_UNITY_CONTAINER_LABEL: 'net.slothsoft.role=compose-unity'])

        def shellArguments
        def selectedContainers = []
        helper.registerAllowedMethod('isWindows', []) { false }
        helper.registerAllowedMethod('sh', [Map]) { Map arguments ->
            shellArguments = arguments
            return "first-task\nsecond-task\n"
        }
        registerContainerScope(selectedContainers)

        def withUnity = loadScript('vars/withUnity.groovy')
        withUnity.call {}

        assertEquals(['first-task'], selectedContainers)
        assertEquals(true, shellArguments.returnStdout)
        assertEquals('UTF-8', shellArguments.encoding)
        assertEquals('find Unity container by label', shellArguments.label)
        assertTrue(shellArguments.script.contains('docker ps --filter'))
        assertTrue(shellArguments.script.contains('$JENKINS_UNITY_CONTAINER_LABEL'))
        assertTrue(shellArguments.script.contains("{{.Names}}"))
        assertFalse(helper.callStack.any { it.methodName == 'powershell' })
    }

    @Test
    void discoversUnityContainerByLabelOnWindows() {
        binding.setVariable('env', [JENKINS_UNITY_CONTAINER_LABEL: 'net.slothsoft.role=compose-unity'])

        def powershellArguments
        def selectedContainers = []
        helper.registerAllowedMethod('isWindows', []) { true }
        helper.registerAllowedMethod('powershell', [Map]) { Map arguments ->
            powershellArguments = arguments
            return "windows-task\r\n"
        }
        registerContainerScope(selectedContainers)

        def withUnity = loadScript('vars/withUnity.groovy')
        withUnity.call {}

        assertEquals(['windows-task'], selectedContainers)
        assertEquals(true, powershellArguments.returnStdout)
        assertEquals('UTF-8', powershellArguments.encoding)
        assertEquals('find Unity container by label', powershellArguments.label)
        assertTrue(powershellArguments.script.contains('docker ps --filter'))
        assertTrue(powershellArguments.script.contains('$env:JENKINS_UNITY_CONTAINER_LABEL'))
        assertTrue(powershellArguments.script.contains('$LASTEXITCODE'))
        assertFalse(helper.callStack.any { it.methodName == 'sh' })
    }

    @Test
    void failsWhenNoRunningUnityContainerMatchesLabel() {
        binding.setVariable('env', [JENKINS_UNITY_CONTAINER_LABEL: 'net.slothsoft.role=compose-unity'])
        helper.registerAllowedMethod('isWindows', []) { false }
        helper.registerAllowedMethod('sh', [Map]) { Map ignored -> " \n\r\n" }
        helper.registerAllowedMethod('error', [String]) { String message -> throw new IllegalStateException(message) }

        def withUnity = loadScript('vars/withUnity.groovy')
        def failure = assertThrows(IllegalStateException) {
            withUnity.call {
                throw new AssertionError('Unity must not start')
            }
        }

        assertEquals("No running Unity container matches label 'net.slothsoft.role=compose-unity'.", failure.message)
        assertFalse(helper.callStack.any { it.methodName == 'insideDockerContainer' })
    }

    @Test
    void preservesMissingContainerErrorWithoutNameOrLabel() {
        binding.setVariable('env', [:])
        helper.registerAllowedMethod('error', [String]) { String message -> throw new IllegalStateException(message) }

        def withUnity = loadScript('vars/withUnity.groovy')
        def failure = assertThrows(IllegalStateException) {
            withUnity.call {
                throw new AssertionError('Unity must not start')
            }
        }

        assertEquals("Invalid Unity container name 'null'.", failure.message)
        assertFalse(helper.callStack.any { it.methodName in ['isWindows', 'powershell', 'sh', 'insideDockerContainer'] })
    }

    @Test
    void reusesDiscoveredContainerForNestedUnityScope() {
        binding.setVariable('env', [JENKINS_UNITY_CONTAINER_LABEL: 'net.slothsoft.role=compose-unity'])

        def selectedContainers = []
        helper.registerAllowedMethod('isWindows', []) { false }
        helper.registerAllowedMethod('sh', [Map]) { Map ignored -> 'swarm-task' }
        helper.registerAllowedMethod('insideDockerContainer', [Map, Closure]) { Map arguments, Closure body ->
            selectedContainers << arguments.container
            env.PIPELINE_DOCKER_CONTAINER_NAME = arguments.container
            env.PIPELINE_DOCKER_CONTAINER_ID = "${arguments.container}-id"
            env.PIPELINE_DOCKER_CONTAINER_OS = 'linux'
            body()
        }
        helper.registerAllowedMethod('withEnv', [List, Closure]) { List environment, Closure body ->
            environment.each { entry ->
                def parts = entry.toString().split('=', 2)
                env[parts[0]] = parts[1]
            }
            body()
        }

        def withUnity = loadScript('vars/withUnity.groovy')
        withUnity.call {
            withUnity.call {}
        }

        assertEquals(['swarm-task', 'swarm-task'], selectedContainers)
        assertEquals(1, helper.callStack.count { it.methodName == 'sh' })
    }

    @Test
    void discoversAgainForEachIndependentUnityScope() {
        binding.setVariable('env', [JENKINS_UNITY_CONTAINER_LABEL: 'net.slothsoft.role=compose-unity'])

        def discoveredContainers = ['first-task', 'replacement-task']
        def selectedContainers = []
        helper.registerAllowedMethod('isWindows', []) { false }
        helper.registerAllowedMethod('sh', [Map]) { Map ignored -> discoveredContainers.remove(0) }
        registerContainerScope(selectedContainers)

        def withUnity = loadScript('vars/withUnity.groovy')
        withUnity.call {}
        withUnity.call {}

        assertEquals(['first-task', 'replacement-task'], selectedContainers)
        assertEquals(2, helper.callStack.count { it.methodName == 'sh' })
    }

    private void registerContainerScope(List selectedContainers) {
        helper.registerAllowedMethod('insideDockerContainer', [Map, Closure]) { Map arguments, Closure body ->
            selectedContainers << arguments.container
            env.PIPELINE_DOCKER_CONTAINER_NAME = arguments.container
            env.PIPELINE_DOCKER_CONTAINER_ID = "${arguments.container}-id"
            env.PIPELINE_DOCKER_CONTAINER_OS = 'linux'
            body()
        }
        helper.registerAllowedMethod('withEnv', [List, Closure]) { List ignored, Closure body -> body() }
    }
}
