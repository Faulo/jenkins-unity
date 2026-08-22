package net.slothsoft.jenkins.unity

import com.cloudbees.groovy.cps.NonCPS

final class PreparedUnityPackage implements Serializable {
    private static final long serialVersionUID = 1L

    final UnityPackageOptions options
    final UnityPackageContext context
    final String executionId
    final String packageStash
    final String configurationStash

    PreparedUnityPackage(UnityPackageOptions options, UnityPackageContext context, String executionId, String packageStash, String configurationStash = '') {
        if (!options || !context || !executionId || !packageStash) {
            throw new IllegalArgumentException('Prepared package data must not be empty')
        }
        this.options = options
        this.context = context
        this.executionId = executionId
        this.packageStash = packageStash
        this.configurationStash = configurationStash ?: ''
    }
}
