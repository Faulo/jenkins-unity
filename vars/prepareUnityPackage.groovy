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
    call(options, [:])
}

PreparedUnityPackage call(UnityPackageOptions options, Map checkoutVariables) {
    def workspace = pwd()
    def packageDirectory = "${workspace}/${options.packageLocation}"
    def branch = resolveBranch(options, checkoutVariables)

    def discovered = dir(packageDirectory) {
        def packageData = readJSON(file: 'package.json')
        [
            id: options.packageId ?: packageData.name?.toString(),
            version: options.packageVersion ?: packageData.version?.toString(),
        ]
    }
    def context = new UnityPackageContext(discovered.id, discovered.version, branch.toString(), options.packageLocation)

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
    if (options.testFormatting) {
        configurationStash = "unity-package-configuration-${executionId}"
        def includes = ([options.formattingLocation] + options.formattingAddons).unique()
        stash(
            name: configurationStash,
            includes: includes.join(', '),
            allowEmpty: true,
            useDefaultExcludes: true
        )
    }

    new PreparedUnityPackage(options, context, executionId, packageStash, configurationStash)
}

private String resolveBranch(UnityPackageOptions options, Map checkoutVariables) {
    options.packageBranch ?:
        env.BRANCH_NAME ?:
        normalizeGitBranch(checkoutVariables?.GIT_LOCAL_BRANCH) ?:
        normalizeGitBranch(checkoutVariables?.GIT_BRANCH) ?:
        normalizeGitBranch(env.GIT_LOCAL_BRANCH) ?:
        normalizeGitBranch(env.GIT_BRANCH) ?:
        env.PLASTICSCM_BRANCH
}

private String normalizeGitBranch(Object value) {
    def branch = value?.toString() ?: ''
    for (String prefix : ['refs/remotes/origin/', 'refs/heads/', 'origin/', '*/']) {
        if (branch.startsWith(prefix)) {
            return branch.substring(prefix.length())
        }
    }
    branch
}
