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
            TEST_CHANGELOG: false,
            CHANGELOG_LOCATION: 'Documentation/CHANGELOG.md',
            TEST_FORMATTING: false,
            FORMATTING_LOCATION: 'config/.editorconfig',
            FORMATTING_ADDONS: ['config/stylecop.json'],
            TEST_UNITY: false,
            UNITY_TEST_MODES: ['EditMode'],
            UNITY_MANIFEST_LOCATION: '.jenkins/manifest.json',
            PUBLISH_BRANCHES: ['release'],
        ])

        assertEquals('Packages/net.example.test', options.packageLocation)
        assertEquals('release', options.packageBranch)
        assertFalse(options.testChangelog)
        assertEquals('Documentation/CHANGELOG.md', options.changelogLocation)
        assertFalse(options.testFormatting)
        assertEquals('config/.editorconfig', options.formattingLocation)
        assertEquals(['config/stylecop.json'], options.formattingAddons)
        assertEquals(['Library'], options.formattingExclusions)
        assertFalse(options.testUnity)
        assertEquals(['EditMode'], options.unityTestModes)
        assertEquals('.jenkins/manifest.json', options.unityManifestLocation)
        assertEquals(['release'], options.publishBranches)
        assertEquals(['.git/**'], options.sourceExcludes)
        assertEquals('/verdaccio/storage', options.verdaccioStorage)
        assertFalse(options.publishToVerdaccio)
    }

    @Test
    void rejectsCompatibilityValuesAndUnknownKeys() {
        assertThrows(IllegalArgumentException) {
            UnityPackageOptions.fromMap([TEST_UNITY: '1'])
        }
        assertThrows(IllegalArgumentException) {
            UnityPackageOptions.fromMap([RUN_UNITY_TESTS: true])
        }
        assertThrows(IllegalArgumentException) {
            UnityPackageOptions.fromMap([PACKAGE_LOCATION: '../package'])
        }
        assertThrows(IllegalArgumentException) {
            UnityPackageOptions.fromMap([UNITY_MANIFEST_LOCATION: '../manifest.json'])
        }
        assertThrows(IllegalArgumentException) {
            UnityPackageOptions.fromMap([
                UNITY_MANIFEST_CREDENTIALS: 'unity-manifest',
                UNITY_MANIFEST_LOCATION: '.jenkins/manifest.json',
            ])
        }
    }

    @Test
    void keepsInfrastructureSeparateAndConfigurable() {
        def defaults = UnityPackagePipelineOptions.fromMap()
        assertEquals('linux && docker', defaults.prepareAgent)
        assertEquals('node:slim', defaults.prepareImage)
        assertEquals('linux && docker', defaults.publishAgent)
        assertEquals('node:slim', defaults.publishImage)
        assertEquals('--network verdaccio', defaults.publishArgs)
        assertEquals([Unity: 'compose-unity'], defaults.unityAgents)
        assertEquals(['Library'], defaults.packageOptions.formattingExclusions)

        def options = UnityPackagePipelineOptions.fromMap([
            PREPARE_AGENT: 'node-prepare',
            PREPARE_IMAGE: 'node:24-bookworm-slim',
            PREPARE_ARGS: '--network host',
            PUBLISH_AGENT: 'node-publish',
            PUBLISH_IMAGE: 'node:22-alpine',
            PUBLISH_ARGS: '--user 1000:1000',
            UNITY_AGENTS: [Editor: 'custom-editor', Player: 'custom-player', WebGL: 'custom-webgl'],
            PACKAGE_LOCATION: 'Package',
        ])

        assertEquals('node-prepare', options.prepareAgent)
        assertEquals('node:24-bookworm-slim', options.prepareImage)
        assertEquals('--network host', options.prepareArgs)
        assertEquals('node-publish', options.publishAgent)
        assertEquals('node:22-alpine', options.publishImage)
        assertEquals('--user 1000:1000', options.publishArgs)
        assertEquals([Editor: 'custom-editor', Player: 'custom-player', WebGL: 'custom-webgl'], options.unityAgents)
        assertEquals('Package', options.packageOptions.packageLocation)

        assertEquals([:], UnityPackagePipelineOptions.fromMap([UNITY_AGENTS: [:]]).unityAgents)
        assertThrows(IllegalArgumentException) {
            UnityPackagePipelineOptions.fromMap([UNITY_AGENTS: []])
        }
    }

    @Test
    void preparedPackageIsImmutableAndSerializable() {
        def options = UnityPackageOptions.fromMap([
            UNITY_MANIFEST_LOCATION: '.jenkins/manifest.json',
            PUBLISH_BRANCHES: ['main'],
        ])
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
        assertEquals('.jenkins/manifest.json', restored.options.unityManifestLocation)
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
