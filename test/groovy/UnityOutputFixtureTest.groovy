import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path
import java.util.concurrent.TimeUnit

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

class UnityOutputFixtureTest {
    @TempDir
    Path directory

    @Test
    void integrationFixtureEmitsXmlBeforeExitAndReturnsFailureStatus() {
        def windows = System.getProperty('os.name').startsWith('Windows')
        def pipeline = new GroovyShell(new Binding([env: [:]])).parse(new File('.jenkins/tests/integration.groovy'))
        pipeline.metaClass.isWindows = { windows }
        pipeline.metaClass.pwd = { -> directory.toString() }
        pipeline.metaClass.pwd = { Map ignored -> directory.toString() }
        pipeline.metaClass.dir = { String ignored, Closure body -> body() }
        pipeline.metaClass.deleteDir = { -> }
        pipeline.metaClass.writeFile = { Map args -> directory.resolve(args.file).toFile().setText(args.text.toString(), 'UTF-8') }
        pipeline.metaClass.withEnv = { List ignored, Closure body -> }
        pipeline.verifyUnityOutputReports()

        def command = windows
            ? ['pwsh', '-NoProfile', '-File', directory.resolve('launcher.ps1').toString(), 'tests', '--junit', '-', 'fail']
            : ['sh', directory.resolve('launcher.sh').toString(), 'tests', '--junit', '-', 'fail']
        def process = new ProcessBuilder(command).directory(directory.toFile()).start()
        try {
            // Read in another thread with a deadline so a broken fixture cannot hang the suite.
            def reader = new BufferedReader(process.inputStream.newReader('UTF-8'))
            def executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            try {
                def output = executor.submit({ -> [reader.readLine(), reader.readLine(), reader.readLine()] } as java.util.concurrent.Callable)
                def lines = output.get(15, TimeUnit.SECONDS)
                assertEquals('  ', lines[0])
                assertTrue(lines[1].startsWith('<testsuite '))
                assertTrue(lines[1].endsWith('</testsuite>'))
                assertEquals('', lines[2])
                assertFalse(process.waitFor(100, TimeUnit.MILLISECONDS))
                directory.resolve('release').toFile().text = 'continue'
                assertTrue(process.waitFor(15, TimeUnit.SECONDS))
                assertEquals(7, process.exitValue())
                assertEquals('diagnostic', process.errorStream.getText('UTF-8').trim())
            } finally {
                executor.shutdownNow()
            }
        } finally {
            process.destroyForcibly()
        }
    }
}
