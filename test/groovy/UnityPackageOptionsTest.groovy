import net.slothsoft.jenkins.unity.PreparedUnityPackage
import net.slothsoft.jenkins.unity.UnityPackageContext
import net.slothsoft.jenkins.unity.UnityPackageOptions
import net.slothsoft.jenkins.unity.UnityPackagePipelineOptions
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class UnityPackageOptionsTest {
    @Test
    void normalizesFreshTypedConfiguration() {
        def options = UnityPackageOptions.fromMap([
            PACKAGE_LOCATION: 'Packages/net.example.test',
            PACKAGE_BRANCH: 'release',
            VALIDATE_CHANGELOG: false,
            UNITY_TEST_MODES: ['EditMode'],
            PUBLISH_BRANCHES: ['release'],
        ])

        assertEquals('Packages/net.example.test', options.packageLocation)
        assertEquals('release', options.packageBranch)
        assertFalse(options.validateChangelog)
        assertEquals(['EditMode'], options.unityTestModes)
        assertEquals(['release'], options.publishBranches)
        assertTrue(options.checkFormatting)
        assertTrue(options.runUnityTests)
        assertFalse(options.publishToVerdaccio)
    }

    @Test
    void rejectsCompatibilityValuesAndUnknownKeys() {
        assertThrows(IllegalArgumentException) {
            UnityPackageOptions.fromMap([RUN_UNITY_TESTS: '1'])
        }
        assertThrows(IllegalArgumentException) {
            UnityPackageOptions.fromMap([TEST_UNITY: true])
        }
        assertThrows(IllegalArgumentException) {
            UnityPackageOptions.fromMap([PACKAGE_LOCATION: '../package'])
        }
    }

    @Test
    void keepsInfrastructureSeparateAndConfigurable() {
        def options = UnityPackagePipelineOptions.fromMap([
            PREPARE_AGENT: 'node-prepare',
            PREPARE_DOCKER_IMAGE: 'node:24-bookworm-slim',
            PUBLISH_AGENT: 'node-publish',
            PUBLISH_DOCKER_IMAGE: 'node:22-alpine',
            UNITY_AGENTS: [linux: 'custom-linux', windows: 'custom-windows'],
            UNITY_CONTAINERS: [linux: 'unity-linux', windows: 'unity-windows'],
            PACKAGE_LOCATION: 'Package',
        ])

        assertEquals('node-prepare', options.prepareAgent)
        assertEquals('node:24-bookworm-slim', options.prepareDockerImage)
        assertEquals('node-publish', options.publishAgent)
        assertEquals('node:22-alpine', options.publishDockerImage)
        assertEquals([linux: 'custom-linux', windows: 'custom-windows'], options.unityAgents)
        assertEquals([linux: 'unity-linux', windows: 'unity-windows'], options.unityContainers)
        assertEquals('Package', options.packageOptions.packageLocation)
    }

    @Test
    void preparedPackageIsImmutableAndSerializable() {
        def options = UnityPackageOptions.fromMap([PUBLISH_BRANCHES: ['main']])
        def context = new UnityPackageContext('net.example.test', '1.2.3-preview.1', 'main', '.')
        def prepared = new PreparedUnityPackage(options, context, 'execution', 'source', 'configuration')

        def bytes = new ByteArrayOutputStream()
        new ObjectOutputStream(bytes).withCloseable { stream ->
            stream.writeObject(prepared)
        }
        PreparedUnityPackage restored
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).withCloseable { stream ->
            restored = stream.readObject() as PreparedUnityPackage
        }

        assertEquals('net.example.test', restored.context.packageId)
        assertEquals('1.2.3-preview.1', restored.context.version)
        assertEquals('1.2.3', restored.context.stableVersion)
        assertFalse(restored.context.release)
        assertEquals(['main'], restored.options.publishBranches)
        assertThrows(UnsupportedOperationException) {
            restored.options.publishBranches << 'other'
        }
    }

    @Test
    void classifiesBuildMetadataWithoutMistakingItForAPrerelease() {
        def context = new UnityPackageContext('net.example.test', '1.2.3+build-with-hyphen', 'main', '.')

        assertTrue(context.release)
        assertEquals('1.2.3', context.stableVersion)
        assertThrows(IllegalArgumentException) {
            new UnityPackageContext('Invalid Package', '1.2.3', 'main', '.')
        }
        assertThrows(IllegalArgumentException) {
            new UnityPackageContext('net.example.test', 'next', 'main', '.')
        }
    }
}
