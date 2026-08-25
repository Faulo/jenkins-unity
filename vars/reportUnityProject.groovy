import net.slothsoft.jenkins.unity.UnityProjectContext

void call(UnityProjectContext project) {
    requireProject(project)
    def options = project.options
    if (options.reportToDiscord && shouldReport(options.discordThreshold)) {
        call(project, 'discord')
    }
    if (options.reportToOffice365 && shouldReport(options.office365Threshold)) {
        call(project, 'office365')
    }
    if (options.reportToAdaptiveCards && shouldReport(options.adaptiveCardsThreshold)) {
        call(project, 'adaptiveCards')
    }
}

void call(UnityProjectContext project, String method) {
    requireProject(project)
    def name = "${project.projectId} v${project.version}"
    switch (method) {
        case 'discord':
            reportToDiscord(project.options.discordWebhook, currentBuild, name)
            break
        case 'office365':
            reportToOffice365(project.options.office365Webhook, currentBuild, name)
            break
        case 'adaptiveCards':
            reportToAdaptiveCard(project.options.adaptiveCardsWebhook, currentBuild, name)
            break
        default:
            throw new IllegalArgumentException("Unknown Unity project report method '${method}'")
    }
}

private boolean shouldReport(String threshold) {
    !threshold || currentBuild.resultIsWorseOrEqualTo(threshold)
}

private void requireProject(UnityProjectContext project) {
    if (!project) {
        throw new IllegalArgumentException('project must not be null')
    }
}
