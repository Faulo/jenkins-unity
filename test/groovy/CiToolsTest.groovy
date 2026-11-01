import hudson.model.Result
import net.slothsoft.jenkins.unity.CiTools
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class CiToolsTest {
    @Test
    void deploysWithNativeShellsAndPlatformSpecificQuoting() {
        for (boolean windows : [false, true]) {
            def state = [containers: [], commands: [], files: [], active: false]
            def steps = steps(state, windows)
            CiTools.steam(steps, 'reports', '1234', ['11=My Game-windows'], 'feature/example')
            CiTools.itch(steps, 'reports', [[source: "My Game's build", target: 'author/game:windows-x64']])
            assertEquals(2, state.containers.size())
            assertTrue(state.containers[0].endsWith(windows ? ':C:/steam' : ':/root/Steam'))
            assertEquals('', state.containers[1])
            assertEquals('  generated VDF\n', state.files[0].text)
            assertEquals('UTF-8', state.files[0].encoding)
            assertEquals(4, state.commands.size())
            assertTrue(state.commands[1].script.contains("'11=My Game-windows'"))
            assertTrue(state.commands[2].script.contains(windows ? '$env:STEAM_CREDENTIALS_PSW' : '"$STEAM_CREDENTIALS_PSW"'))
            assertTrue(state.commands[3].script.contains(windows ? "'My Game''s build'" : "'My Game'\"'\"'s build'"))
            assertTrue(state.commands.every { windows ? it.script.endsWith('exit $LASTEXITCODE }') : it.script.startsWith('set +x\n') })
            assertFalse(state.active)
        }
    }

    @Test
    void failuresAndInterruptionsStopBeforeUploadAndLeaveTheContainer() {
        for (Throwable failure : [new IllegalStateException('exit 7'), new FlowInterruptedException(Result.ABORTED, true)]) {
            for (boolean windows : [false, true]) {
                def state = [containers: [], commands: [], files: [], active: false]
                def steps = steps(state, windows)
                steps.sh = { Map ignored -> throw failure }
                steps.pwsh = { Map ignored -> throw failure }
                assertSame(failure, assertThrows(failure.class) { CiTools.steam(steps, 'reports', '1234', ['11=build'], 'main') })
                assertFalse(state.active)
                assertTrue(state.files.empty)
            }
        }
    }

    @Test
    void reusesSteamStateWithinOneWorkspaceAndSeparatesWorkspaces() {
        assertEquals(CiTools.steamVolume('/workspace/reports'), CiTools.steamVolume('/workspace/reports'))
        assertNotEquals(CiTools.steamVolume('/workspace/reports'), CiTools.steamVolume('/workspace@2/reports'))
        assertTrue(CiTools.steamVolume('C:/workspace/My Project').matches('[a-z0-9-]+'))
    }

    private static def steps(Map state, boolean windows) {
        new Expando(
            isUnix: { !windows },
            pwd: { windows ? 'C:/workspace/reports' : '/workspace/reports' },
            dir: { String ignored, Closure body -> body() },
            writeFile: { Map args -> state.files << args },
            docker: new Expando(image: { String image ->
                assertEquals('faulo/ci-tools', image)
                new Expando(inside: { String arguments, Closure body ->
                    state.containers << arguments
                    state.active = true
                    try { body() } finally { state.active = false }
                })
            }),
            sh: { Map args -> assertTrue(state.active); state.commands << args; args.returnStdout ? '  generated VDF\n' : null },
            pwsh: { Map args -> assertTrue(state.active); state.commands << args; args.returnStdout ? '  generated VDF\n' : null }
        )
    }
}
