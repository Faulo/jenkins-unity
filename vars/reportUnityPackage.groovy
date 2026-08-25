import net.slothsoft.jenkins.unity.PreparedUnityPackage
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

void call(PreparedUnityPackage preparedPackage) {
    requirePreparedPackage(preparedPackage)
    def options = preparedPackage.options
    try {
        if (options.reportToDiscord && shouldReport(options.discordThreshold)) {
            call(preparedPackage, 'discord')
        }
        if (options.reportToOffice365 && shouldReport(options.office365Threshold)) {
            call(preparedPackage, 'office365')
        }
        if (options.reportToAdaptiveCards && shouldReport(options.adaptiveCardsThreshold)) {
            call(preparedPackage, 'adaptiveCards')
        }
    } catch (FlowInterruptedException e) {
        currentBuild.result = e.result
        throw e
    }
}

void call(PreparedUnityPackage preparedPackage, String method) {
    requirePreparedPackage(preparedPackage)
    def options = preparedPackage.options
    def name = "${preparedPackage.context.packageId} v${preparedPackage.context.version}"
    try {
        switch (method) {
            case 'discord':
                reportToDiscord(options.discordWebhook, currentBuild, name)
                break
            case 'office365':
                reportToOffice365(options.office365Webhook, currentBuild, name)
                break
            case 'adaptiveCards':
                reportToAdaptiveCard(options.adaptiveCardsWebhook, currentBuild, name)
                break
            default:
                throw new IllegalArgumentException("Unknown Unity package report method '${method}'")
        }
    } catch (FlowInterruptedException e) {
        currentBuild.result = e.result
        throw e
    }
}

private boolean shouldReport(String threshold) {
    !threshold || currentBuild.resultIsWorseOrEqualTo(threshold)
}

private void requirePreparedPackage(PreparedUnityPackage preparedPackage) {
    if (!preparedPackage) {
        throw new IllegalArgumentException('preparedPackage must not be null')
    }
}
