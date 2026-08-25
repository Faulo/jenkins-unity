package net.slothsoft.jenkins.unity

import com.cloudbees.groovy.cps.NonCPS

final class UnityProjectOptions implements Serializable {
    private static final long serialVersionUID = 1L

    static final Map<String, Object> DEFAULTS = Collections.unmodifiableMap([
        PROJECT_LOCATION: '.',
        PROJECT_ID: '',
        PROJECT_VERSION: '',
        PROJECT_BRANCH: '',

        AUTOVERSION: '',
        AUTOVERSION_REVISION: false,
        AUTOVERSION_REVISION_PREFIX: '',
        AUTOVERSION_REVISION_SUFFIX: '',

        TEST_FORMATTING: true,
        FORMATTING_LOCATION: '.editorconfig',
        FORMATTING_EXCLUSIONS: ['Library'],
        TEST_UNITY: true,
        UNITY_TEST_MODES: ['EditMode', 'PlayMode'],
        BUILD_DOCUMENTATION: false,

        BUILD_FOR_WINDOWS: false,
        BUILD_FOR_LINUX: false,
        BUILD_FOR_MAC: false,
        BUILD_FOR_WEBGL: false,
        BUILD_FOR_ANDROID: false,
        BUILD_NAME: 'build',

        UNITY_CREDENTIALS: '',
        EMAIL_CREDENTIALS: '',

        DEPLOY_ON_FAILURE: false,
        DEPLOYMENT_BRANCHES: ['main', '/main'],
        DEPLOY_TO_STEAM: false,
        STEAM_CREDENTIALS: '',
        STEAM_ID: '',
        STEAM_DEPOT_WINDOWS: '',
        STEAM_DEPOT_LINUX: '',
        STEAM_DEPOT_MAC: '',
        STEAM_BRANCH: '',
        DEPLOY_TO_ITCH: false,
        ITCH_CREDENTIALS: '',
        ITCH_ID: '',

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

    final String projectLocation
    final String projectId
    final String projectVersion
    final String projectBranch
    final String autoversion
    final boolean autoversionRevision
    final String autoversionRevisionPrefix
    final String autoversionRevisionSuffix
    final boolean testFormatting
    final String formattingLocation
    final List<String> formattingExclusions
    final boolean testUnity
    final List<String> unityTestModes
    final boolean buildDocumentation
    final boolean buildForWindows
    final boolean buildForLinux
    final boolean buildForMac
    final boolean buildForWebGL
    final boolean buildForAndroid
    final String buildName
    final String unityCredentialsId
    final String emailCredentialsId
    final boolean deployOnFailure
    final List<String> deploymentBranches
    final boolean deployToSteam
    final String steamCredentialsId
    final String steamId
    final String steamDepotWindows
    final String steamDepotLinux
    final String steamDepotMac
    final String steamBranch
    final boolean deployToItch
    final String itchCredentialsId
    final String itchId
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
    static UnityProjectOptions fromMap(Map values = [:]) {
        new UnityProjectOptions(UnityPackageConfig.normalize(values, DEFAULTS))
    }

    private UnityProjectOptions(Map values) {
        projectLocation = UnityPackageConfig.stringValue(values, 'PROJECT_LOCATION')
        UnityPackageConfig.requireRelativePath(projectLocation, 'PROJECT_LOCATION')
        projectId = UnityPackageConfig.stringValue(values, 'PROJECT_ID')
        projectVersion = UnityPackageConfig.stringValue(values, 'PROJECT_VERSION')
        projectBranch = UnityPackageConfig.stringValue(values, 'PROJECT_BRANCH')
        autoversion = UnityPackageConfig.stringValue(values, 'AUTOVERSION')
        autoversionRevision = UnityPackageConfig.booleanValue(values, 'AUTOVERSION_REVISION')
        autoversionRevisionPrefix = UnityPackageConfig.stringValue(values, 'AUTOVERSION_REVISION_PREFIX')
        autoversionRevisionSuffix = UnityPackageConfig.stringValue(values, 'AUTOVERSION_REVISION_SUFFIX')
        testFormatting = UnityPackageConfig.booleanValue(values, 'TEST_FORMATTING')
        formattingLocation = UnityPackageConfig.stringValue(values, 'FORMATTING_LOCATION')
        UnityPackageConfig.requireRelativePath(formattingLocation, 'FORMATTING_LOCATION')
        formattingExclusions = UnityPackageConfig.stringList(values, 'FORMATTING_EXCLUSIONS')
        testUnity = UnityPackageConfig.booleanValue(values, 'TEST_UNITY')
        unityTestModes = UnityPackageConfig.stringList(values, 'UNITY_TEST_MODES', !testUnity)
        buildDocumentation = UnityPackageConfig.booleanValue(values, 'BUILD_DOCUMENTATION')
        buildForWindows = UnityPackageConfig.booleanValue(values, 'BUILD_FOR_WINDOWS')
        buildForLinux = UnityPackageConfig.booleanValue(values, 'BUILD_FOR_LINUX')
        buildForMac = UnityPackageConfig.booleanValue(values, 'BUILD_FOR_MAC')
        buildForWebGL = UnityPackageConfig.booleanValue(values, 'BUILD_FOR_WEBGL')
        buildForAndroid = UnityPackageConfig.booleanValue(values, 'BUILD_FOR_ANDROID')
        buildName = UnityPackageConfig.stringValue(values, 'BUILD_NAME')
        unityCredentialsId = UnityPackageConfig.stringValue(values, 'UNITY_CREDENTIALS')
        emailCredentialsId = UnityPackageConfig.stringValue(values, 'EMAIL_CREDENTIALS')
        deployOnFailure = UnityPackageConfig.booleanValue(values, 'DEPLOY_ON_FAILURE')
        deploymentBranches = UnityPackageConfig.stringList(values, 'DEPLOYMENT_BRANCHES', false)
        deployToSteam = UnityPackageConfig.booleanValue(values, 'DEPLOY_TO_STEAM')
        steamCredentialsId = UnityPackageConfig.stringValue(values, 'STEAM_CREDENTIALS')
        steamId = UnityPackageConfig.stringValue(values, 'STEAM_ID')
        steamDepotWindows = UnityPackageConfig.stringValue(values, 'STEAM_DEPOT_WINDOWS')
        steamDepotLinux = UnityPackageConfig.stringValue(values, 'STEAM_DEPOT_LINUX')
        steamDepotMac = UnityPackageConfig.stringValue(values, 'STEAM_DEPOT_MAC')
        steamBranch = UnityPackageConfig.stringValue(values, 'STEAM_BRANCH')
        deployToItch = UnityPackageConfig.booleanValue(values, 'DEPLOY_TO_ITCH')
        itchCredentialsId = UnityPackageConfig.stringValue(values, 'ITCH_CREDENTIALS')
        itchId = UnityPackageConfig.stringValue(values, 'ITCH_ID')
        reportToDiscord = UnityPackageConfig.booleanValue(values, 'REPORT_TO_DISCORD')
        discordWebhook = UnityPackageConfig.stringValue(values, 'DISCORD_WEBHOOK')
        discordThreshold = UnityPackageConfig.stringValue(values, 'DISCORD_THRESHOLD')
        reportToOffice365 = UnityPackageConfig.booleanValue(values, 'REPORT_TO_OFFICE_365')
        office365Webhook = UnityPackageConfig.stringValue(values, 'OFFICE_365_WEBHOOK')
        office365Threshold = UnityPackageConfig.stringValue(values, 'OFFICE_365_THRESHOLD')
        reportToAdaptiveCards = UnityPackageConfig.booleanValue(values, 'REPORT_TO_ADAPTIVE_CARDS')
        adaptiveCardsWebhook = UnityPackageConfig.stringValue(values, 'ADAPTIVE_CARDS_WEBHOOK')
        adaptiveCardsThreshold = UnityPackageConfig.stringValue(values, 'ADAPTIVE_CARDS_THRESHOLD')

        if (!buildName) {
            throw new IllegalArgumentException('BUILD_NAME must not be empty')
        }
        if (deployToSteam && (!steamCredentialsId || !steamId)) {
            throw new IllegalArgumentException('STEAM_CREDENTIALS and STEAM_ID are required when DEPLOY_TO_STEAM is enabled')
        }
        def hasSteamDepot = (buildForWindows && steamDepotWindows) || (buildForLinux && steamDepotLinux) || (buildForMac && steamDepotMac)
        if (deployToSteam && !hasSteamDepot) {
            throw new IllegalArgumentException('DEPLOY_TO_STEAM requires at least one enabled desktop build with a depot ID')
        }
        if (deployToItch && (!itchCredentialsId || !itchId || !hasPlayerBuild())) {
            throw new IllegalArgumentException('DEPLOY_TO_ITCH requires ITCH_CREDENTIALS, ITCH_ID, and at least one enabled player build')
        }
    }

    @NonCPS
    boolean hasPlayerBuild() {
        buildForWindows || buildForLinux || buildForMac || buildForWebGL || buildForAndroid
    }

    @NonCPS
    boolean hasReporting() {
        reportToDiscord || reportToOffice365 || reportToAdaptiveCards
    }
}
