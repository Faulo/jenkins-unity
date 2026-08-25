import net.slothsoft.jenkins.unity.InstalledUnityPackage

void call(InstalledUnityPackage installedPackage) {
    if (!installedPackage) {
        return
    }
    dir(installedPackage.workDirectory) {
        deleteDir()
    }
}
