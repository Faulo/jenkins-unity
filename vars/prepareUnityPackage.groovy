import com.cloudbees.groovy.cps.NonCPS
import net.slothsoft.jenkins.unity.PreparedUnityPackage
import net.slothsoft.jenkins.unity.UnityPackageContext
import net.slothsoft.jenkins.unity.UnityPackageOptions

PreparedUnityPackage call(Closure body) {
    def args = [:]
    def originalDelegate = body.delegate
    def originalResolveStrategy = body.resolveStrategy
    try {
        body.delegate = args
        body.resolveStrategy = Closure.DELEGATE_FIRST
        body()
    } finally {
        body.delegate = originalDelegate
        body.resolveStrategy = originalResolveStrategy
    }
    call(UnityPackageOptions.fromMap(args))
}

PreparedUnityPackage call(Map args) {
    call(UnityPackageOptions.fromMap(args))
}

PreparedUnityPackage call(UnityPackageOptions options) {
    def workspace = pwd()
    def packageDirectory = "${workspace}/${options.packageLocation}"
    if (!fileExists(packageDirectory)) {
        error "Package folder '${options.packageLocation}' does not exist in the current workspace."
    }

    def branch = options.packageBranch ?: env.BRANCH_NAME ?: env.PLASTICSCM_BRANCH
    if (!branch) {
        error 'PACKAGE_BRANCH, BRANCH_NAME, or PLASTICSCM_BRANCH is required.'
    }

    def discovered = dir(packageDirectory) {
        def packageData = readJSON(file: 'package.json')
        [
            id: options.packageId ?: packageData.name?.toString(),
            version: options.packageVersion ?: packageData.version?.toString(),
        ]
    }
    def context = new UnityPackageContext(discovered.id, discovered.version, branch.toString(), options.packageLocation)

    if (options.validateChangelog) {
        dir(packageDirectory) {
            if (!fileExists(options.changelogFile)) {
                unstable "Changelog at '${options.changelogFile}' is missing."
            } else {
                def changelogContent = readFile(options.changelogFile)
                def validChangelog = containsDatedVersion(changelogContent, context.version)
                if (!context.release) {
                    validChangelog = validChangelog || containsDatedVersion(changelogContent, context.stableVersion)
                }
                if (!validChangelog) {
                    unstable "${options.changelogFile} does not contain a dated entry for ${context.version}${context.release ? '' : " or ${context.stableVersion}"}."
                }
            }
        }
    }

    if (options.checkFormatting && !fileExists("${workspace}/${options.editorConfigFile}")) {
        error "EditorConfig file '${options.editorConfigFile}' does not exist in the current workspace."
    }

    def executionId = UUID.randomUUID().toString()
    def packageStash = "unity-package-source-${executionId}"
    dir(packageDirectory) {
        def stashArgs = [
            name: packageStash,
            includes: options.sourceIncludes.join(', '),
            useDefaultExcludes: true,
        ]
        if (options.sourceExcludes) {
            stashArgs.excludes = options.sourceExcludes.join(', ')
        }
        stash(stashArgs)
    }

    def configurationStash = ''
    if (options.checkFormatting) {
        configurationStash = "unity-package-configuration-${executionId}"
        def includes = ([options.editorConfigFile] + options.formattingFiles).unique()
        stash(
            name: configurationStash,
            includes: includes.join(', '),
            allowEmpty: false,
            useDefaultExcludes: true
        )
    }

    new PreparedUnityPackage(options, context, executionId, packageStash, configurationStash)
}

@NonCPS
private boolean containsDatedVersion(String changelog, String version) {
    def expected = ~/^## \[${java.util.regex.Pattern.quote(version)}\] - \d{4}-\d{2}-\d{2}$/
    changelog.readLines().any { line -> line ==~ expected }
}
