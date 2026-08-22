package net.slothsoft.jenkins.unity

import com.cloudbees.groovy.cps.NonCPS

final class UnityPackageOptions implements Serializable {
    private static final long serialVersionUID = 1L

    static final Map<String, Object> DEFAULTS = Collections.unmodifiableMap([
        PACKAGE_LOCATION: '.',
        PACKAGE_ID: '',
        PACKAGE_VERSION: '',
        PACKAGE_BRANCH: '',
        SOURCE_INCLUDES: ['**'],
        SOURCE_EXCLUDES: ['.git/**', 'Library/**', 'Logs/**', 'Obj/**', 'Temp/**', 'UserSettings/**'],

        VALIDATE_CHANGELOG: true,
        CHANGELOG_FILE: 'CHANGELOG.md',
        CHECK_FORMATTING: true,
        EDITORCONFIG_FILE: '.editorconfig',
        FORMATTING_FILES: ['.editor/**', 'Directory.Build.props'],
        FORMATTING_EXCLUDE: [],
        RUN_UNITY_TESTS: true,
        UNITY_TEST_MODES: ['EditMode', 'PlayMode'],
        BUILD_DOCUMENTATION: false,

        UNITY_CREDENTIALS: '',
        EMAIL_CREDENTIALS: '',
        UNITY_MANIFEST_CREDENTIALS: '',

        PUBLISH_TO_VERDACCIO: false,
        PUBLISH_ON_FAILURE: false,
        PUBLISH_RELEASES: true,
        PUBLISH_PRERELEASES: true,
        PUBLISH_BRANCHES: ['main', '/main'],
        VERDACCIO_URL: 'http://verdaccio:4873',
        VERDACCIO_HOST: 'verdaccio:4873',
        VERDACCIO_STORAGE: '',
        VERDACCIO_CREDENTIALS: '',

        REPORT_TO_DISCORD: false,
        DISCORD_WEBHOOK: '',
        DISCORD_THRESHOLD: '',
        REPORT_TO_OFFICE_365: false,
        OFFICE_365_WEBHOOK: '',
        OFFICE_365_THRESHOLD: '',
        REPORT_TO_ADAPTIVE_CARDS: false,
        ADAPTIVE_CARDS_WEBHOOK: '',
        ADAPTIVE_CARDS_THRESHOLD: '',
    ])

    final String packageLocation
    final String packageId
    final String packageVersion
    final String packageBranch
    final List<String> sourceIncludes
    final List<String> sourceExcludes
    final boolean validateChangelog
    final String changelogFile
    final boolean checkFormatting
    final String editorConfigFile
    final List<String> formattingFiles
    final List<String> formattingExclude
    final boolean runUnityTests
    final List<String> unityTestModes
    final boolean buildDocumentation
    final String unityCredentialsId
    final String emailCredentialsId
    final String unityManifestCredentialsId
    final boolean publishToVerdaccio
    final boolean publishOnFailure
    final boolean publishReleases
    final boolean publishPrereleases
    final List<String> publishBranches
    final String verdaccioUrl
    final String verdaccioHost
    final String verdaccioStorage
    final String verdaccioCredentialsId
    final boolean reportToDiscord
    final String discordWebhook
    final String discordThreshold
    final boolean reportToOffice365
    final String office365Webhook
    final String office365Threshold
    final boolean reportToAdaptiveCards
    final String adaptiveCardsWebhook
    final String adaptiveCardsThreshold

    @NonCPS
    static UnityPackageOptions fromMap(Map values = [:]) {
        new UnityPackageOptions(UnityPackageConfig.normalize(values, DEFAULTS))
    }

    private UnityPackageOptions(Map values) {
        packageLocation = UnityPackageConfig.stringValue(values, 'PACKAGE_LOCATION')
        UnityPackageConfig.requireRelativePath(packageLocation, 'PACKAGE_LOCATION')
        packageId = UnityPackageConfig.stringValue(values, 'PACKAGE_ID')
        packageVersion = UnityPackageConfig.stringValue(values, 'PACKAGE_VERSION')
        packageBranch = UnityPackageConfig.stringValue(values, 'PACKAGE_BRANCH')
        sourceIncludes = UnityPackageConfig.stringList(values, 'SOURCE_INCLUDES', false)
        sourceExcludes = UnityPackageConfig.stringList(values, 'SOURCE_EXCLUDES')
        validateChangelog = UnityPackageConfig.booleanValue(values, 'VALIDATE_CHANGELOG')
        changelogFile = UnityPackageConfig.stringValue(values, 'CHANGELOG_FILE')
        UnityPackageConfig.requireRelativePath(changelogFile, 'CHANGELOG_FILE')
        checkFormatting = UnityPackageConfig.booleanValue(values, 'CHECK_FORMATTING')
        editorConfigFile = UnityPackageConfig.stringValue(values, 'EDITORCONFIG_FILE')
        UnityPackageConfig.requireRelativePath(editorConfigFile, 'EDITORCONFIG_FILE')
        formattingFiles = UnityPackageConfig.stringList(values, 'FORMATTING_FILES')
        formattingExclude = UnityPackageConfig.stringList(values, 'FORMATTING_EXCLUDE')
        runUnityTests = UnityPackageConfig.booleanValue(values, 'RUN_UNITY_TESTS')
        unityTestModes = UnityPackageConfig.stringList(values, 'UNITY_TEST_MODES', !runUnityTests)
        buildDocumentation = UnityPackageConfig.booleanValue(values, 'BUILD_DOCUMENTATION')
        unityCredentialsId = UnityPackageConfig.stringValue(values, 'UNITY_CREDENTIALS')
        emailCredentialsId = UnityPackageConfig.stringValue(values, 'EMAIL_CREDENTIALS')
        unityManifestCredentialsId = UnityPackageConfig.stringValue(values, 'UNITY_MANIFEST_CREDENTIALS')
        publishToVerdaccio = UnityPackageConfig.booleanValue(values, 'PUBLISH_TO_VERDACCIO')
        publishOnFailure = UnityPackageConfig.booleanValue(values, 'PUBLISH_ON_FAILURE')
        publishReleases = UnityPackageConfig.booleanValue(values, 'PUBLISH_RELEASES')
        publishPrereleases = UnityPackageConfig.booleanValue(values, 'PUBLISH_PRERELEASES')
        publishBranches = UnityPackageConfig.stringList(values, 'PUBLISH_BRANCHES', false)
        verdaccioUrl = UnityPackageConfig.stringValue(values, 'VERDACCIO_URL')
        verdaccioHost = UnityPackageConfig.stringValue(values, 'VERDACCIO_HOST')
        verdaccioStorage = UnityPackageConfig.stringValue(values, 'VERDACCIO_STORAGE')
        verdaccioCredentialsId = UnityPackageConfig.stringValue(values, 'VERDACCIO_CREDENTIALS')
        reportToDiscord = UnityPackageConfig.booleanValue(values, 'REPORT_TO_DISCORD')
        discordWebhook = UnityPackageConfig.stringValue(values, 'DISCORD_WEBHOOK')
        discordThreshold = UnityPackageConfig.stringValue(values, 'DISCORD_THRESHOLD')
        reportToOffice365 = UnityPackageConfig.booleanValue(values, 'REPORT_TO_OFFICE_365')
        office365Webhook = UnityPackageConfig.stringValue(values, 'OFFICE_365_WEBHOOK')
        office365Threshold = UnityPackageConfig.stringValue(values, 'OFFICE_365_THRESHOLD')
        reportToAdaptiveCards = UnityPackageConfig.booleanValue(values, 'REPORT_TO_ADAPTIVE_CARDS')
        adaptiveCardsWebhook = UnityPackageConfig.stringValue(values, 'ADAPTIVE_CARDS_WEBHOOK')
        adaptiveCardsThreshold = UnityPackageConfig.stringValue(values, 'ADAPTIVE_CARDS_THRESHOLD')
    }
}
