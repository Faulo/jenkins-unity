import net.slothsoft.jenkins.unity.PreparedUnityPackage
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

void call(PreparedUnityPackage preparedPackage) {
    if (!preparedPackage) {
        throw new IllegalArgumentException('preparedPackage must not be null')
    }

    def options = preparedPackage.options
    def name = "${preparedPackage.context.packageId} v${preparedPackage.context.version}"
    try {
        if (options.reportToDiscord && shouldReport(options.discordThreshold)) {
            reportToDiscord(options.discordWebhook, currentBuild, name)
        }
        if (options.reportToOffice365 && shouldReport(options.office365Threshold)) {
            reportToOffice365(options.office365Webhook, currentBuild, name)
        }
        if (options.reportToAdaptiveCards && shouldReport(options.adaptiveCardsThreshold)) {
            reportToAdaptiveCard(options.adaptiveCardsWebhook, currentBuild, name)
        }
    } catch (FlowInterruptedException e) {
        currentBuild.result = e.result
        throw e
    }
}

private boolean shouldReport(String threshold) {
    !threshold || currentBuild.resultIsWorseOrEqualTo(threshold)
}
