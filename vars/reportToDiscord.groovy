import groovy.json.JsonOutput

def call(String webhookUrl, def currentBuild, String name) {
	String result = currentBuild.currentResult ?: currentBuild.result ?: 'UNKNOWN'
	String description = "${result}: ${name}"
	String footer = buildChangeLogFooter(currentBuild)

	def embed = [
		title      : truncateDiscord(name, 256),
		description: truncateDiscord(description, 4096),
		url        : env.BUILD_URL,
		color      : discordColorForResult(result)
	]

	if (footer?.trim()) {
		embed.footer = [
			text: truncateDiscord(footer.trim(), 2048)
		]
	}

	def payload = [
		embeds: [embed]
	]

	try {
		httpRequest(
				httpMode              : 'POST',
				url                   : webhookUrl,
				contentType           : 'APPLICATION_JSON',
				acceptType            : 'APPLICATION_JSON',
				requestBody           : JsonOutput.toJson(payload),
				validResponseCodes    : '200:299',
				timeout               : 30,
				quiet                 : true,
				consoleLogResponseBody: false
				)
	} catch (Throwable e) {
		if (e instanceof org.jenkinsci.plugins.workflow.steps.FlowInterruptedException) {
			throw e
		}

		echo "Discord notification failed: ${e.class.simpleName}: ${e.message}"
	}
}

String buildChangeLogFooter(def currentBuild) {
	String footer = ""

	def changeSets = currentBuild.changeSets ?: []

	for (changeLogSet in changeSets) {
		for (entry in changeLogSet.getItems()) {
			footer += "- ${entry.msg}\n"
		}
	}

	return footer
}

Integer discordColorForResult(String result) {
	switch (result) {
		case 'SUCCESS':
			return 0x2ECC71 // green
		case 'UNSTABLE':
			return 0xF1C40F // yellow
		case 'FAILURE':
			return 0xE74C3C // red
		case 'ABORTED':
			return 0x95A5A6 // gray
		case 'NOT_BUILT':
			return 0x95A5A6 // gray
		default:
			return 0x3498DB // blue
	}
}

String truncateDiscord(String value, int maxLength) {
	if (value == null) {
		return ""
	}

	if (value.length() <= maxLength) {
		return value
	}

	return value.substring(0, maxLength - 1) + "…"
}