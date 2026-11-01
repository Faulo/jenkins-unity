import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertSame
import static org.junit.jupiter.api.Assertions.assertThrows

class CiIntegrationSuiteTest {
    @Test
    void runsPackageAndProjectPhasesInTheAllocatedWorkspace() {
        def events = []
        def script = integrationScript(events)
        def config = configuration()
        script.packagePhases(config)
        script.projectPhases(config)
        assertEquals([
            'prepare', 'install', 'changelog', 'build:solution', 'test:formatting',
            'build:documentation', 'test:unity', 'delete package',
            'build:solution', 'test:formatting', 'build:documentation', 'test:unity',
            'delete project reports',
        ], events)
    }

    @Test
    void cleansUpBothFixturesAfterPhaseFailure() {
        def events = []
        def script = integrationScript(events)
        def failure = new IllegalStateException('solution failed')
        script.metaClass.buildUnityProject = { Object project, String operation -> throw failure }
        assertSame(failure, assertThrows(IllegalStateException) { script.packagePhases(configuration()) })
        assertEquals('delete package', events.last())
        assertSame(failure, assertThrows(IllegalStateException) { script.projectPhases(configuration()) })
        assertEquals('delete project reports', events.last())
    }

    private static Map configuration() {
        def properties = new Properties()
        new File('.jenkins/testProject.properties').withReader('UTF-8') { properties.load(it) }
        properties.collectEntries { key, value -> [(key.toString()): value.toString()] }
    }

    private static def integrationScript(List events) {
        def script = new GroovyShell().parse(new File('.jenkins/tests/integration.groovy'))
        script.metaClass.stage = { String ignored, Closure body -> body() }
        script.metaClass.pwd = { -> 'C:\\workspace' }
        script.metaClass.pwd = { Map ignored -> 'C:\\workspace@tmp' }
        script.metaClass.prepareUnityPackage = { Map options ->
            assertEquals('.jenkins/fixtures/unity-package', options.PACKAGE_LOCATION)
            assertEquals('.jenkins/manifest.json', options.UNITY_MANIFEST_LOCATION)
            events << 'prepare'
            new Expando()
        }
        script.metaClass.installUnityPackage = { Object ignored ->
            events << 'install'
            new Expando()
        }
        script.metaClass.testUnityPackage = { Object ignored -> events << 'changelog' }
        script.metaClass.buildUnityProject = { Object project, String operation ->
            events << "build:${operation}".toString()
        }
        script.metaClass.testUnityProject = { Object project, String operation ->
            events << "test:${operation}".toString()
        }
        script.metaClass.deleteUnityProject = { Object ignored -> events << 'delete package' }
        script.metaClass.dir = { String path, Closure body ->
            assertEquals('C:/workspace@tmp/project-reports', path)
            body()
        }
        script.metaClass.deleteDir = { -> events << 'delete project reports' }
        script
    }
}
