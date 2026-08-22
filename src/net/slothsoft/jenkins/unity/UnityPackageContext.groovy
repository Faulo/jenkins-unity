package net.slothsoft.jenkins.unity

final class UnityPackageContext implements Serializable {
    private static final long serialVersionUID = 1L

    final String packageId
    final String version
    final String stableVersion
    final boolean release
    final String branch
    final String packageLocation

    UnityPackageContext(String packageId, String version, String branch, String packageLocation) {
        if (!packageId || !version || !branch || !packageLocation) {
            throw new IllegalArgumentException('Prepared package metadata must not be empty')
        }
        if (!(packageId ==~ /^(?:@[a-z0-9][a-z0-9._-]*\/)?[a-z0-9][a-z0-9._-]*$/)) {
            throw new IllegalArgumentException("Invalid npm package ID '${packageId}'")
        }
        if (!(version ==~ /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$/)) {
            throw new IllegalArgumentException("Invalid semantic version '${version}'")
        }
        this.packageId = packageId
        this.version = version
        def versionWithoutBuild = version.tokenize('+')[0]
        this.release = !versionWithoutBuild.contains('-')
        this.stableVersion = release ? versionWithoutBuild : versionWithoutBuild.substring(0, versionWithoutBuild.indexOf('-'))
        this.branch = branch
        this.packageLocation = packageLocation
    }
}
