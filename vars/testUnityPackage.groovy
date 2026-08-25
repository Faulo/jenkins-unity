import com.cloudbees.groovy.cps.NonCPS
import net.slothsoft.jenkins.unity.InstalledUnityPackage

void call(InstalledUnityPackage installedPackage) {
    if (!installedPackage) {
        throw new IllegalArgumentException('installedPackage must not be null')
    }

    def preparedPackage = installedPackage.preparedPackage
    def options = preparedPackage.options
    dir(installedPackage.packageDirectory) {
        if (!fileExists(options.changelogLocation)) {
            unstable "Changelog at '${options.changelogLocation}' is missing."
            return
        }

        def changelogContent = readFile(options.changelogLocation)
        def context = preparedPackage.context
        def validChangelog = containsDatedVersion(changelogContent, context.version)
        if (!context.release) {
            validChangelog = validChangelog || containsDatedVersion(changelogContent, context.stableVersion)
        }
        if (!validChangelog) {
            unstable "${options.changelogLocation} does not contain a dated entry for ${context.version}${context.release ? '' : " or ${context.stableVersion}"}."
        }
    }
}

@NonCPS
private boolean containsDatedVersion(String changelog, String version) {
    def expected = ~/^## \[${java.util.regex.Pattern.quote(version)}\] - \d{4}-\d{2}-\d{2}$/
    changelog.readLines().any { line -> line ==~ expected }
}
