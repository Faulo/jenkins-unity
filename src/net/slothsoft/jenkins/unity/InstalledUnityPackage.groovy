package net.slothsoft.jenkins.unity

final class InstalledUnityPackage implements Serializable {
    private static final long serialVersionUID = 1L

    final PreparedUnityPackage preparedPackage
    final String workDirectory
    final String packageDirectory
    final String projectDirectory
    final String reportsDirectory

    InstalledUnityPackage(PreparedUnityPackage preparedPackage, String workDirectory, String packageDirectory, String projectDirectory, String reportsDirectory) {
        if (!preparedPackage) {
            throw new IllegalArgumentException('preparedPackage must not be null')
        }
        this.preparedPackage = preparedPackage
        this.workDirectory = workDirectory
        this.packageDirectory = packageDirectory
        this.projectDirectory = projectDirectory
        this.reportsDirectory = reportsDirectory
    }
}
