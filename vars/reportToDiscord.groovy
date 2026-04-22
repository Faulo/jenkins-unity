import groovy.json.JsonOutput

def call(String webhookUrl, def currentBuild, String name) {
	String result = currentBuild.currentResult ?: currentBuild.result ?: 'UNKNOWN'
	String title = "${result}: ${name}"
	String description = buildChangeLogDescription(currentBuild)
	String footer = buildChangeLogFooter(currentBuild)

	def embed = [
		title       : truncateDiscord(title, 256),
		description : truncateDiscord(description, 4096),
		url         : env.BUILD_URL,
		color       : discordColorForResult(result),
		footer 		: [
			text: truncateDiscord(footer.trim(), 2048)
		]
	]

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

String buildChangeLogDescription(def currentBuild) {
	String description = ""

	def error = currentBuild.rawBuild?.execution?.causeOfFailure
	if (error) {
		description += "Cause of failure:\r\n"
		description += "${error}\r\n"
	}

	if (buildShouldPing(currentBuild)) {
		description += "Help!\r\n"
		if (env.DISCORD_PING_USER) {
			description += "<@${env.DISCORD_PING_USER}>\r\n"
		}
	}

	return description
}

String buildChangeLogFooter(def currentBuild) {
	String footer = ""

	footer += "Changes:\r\n"
	def hasChanges = false
	for (changeLogSet in currentBuild.changeSets) {
		for (entry in changeLogSet.items) {
			footer += "- ${entry.msg}\r\n"
			hasChanges = true
		}
	}

	if (!hasChanges) {
		footer += "No changes detected.\r\n"
	}

	def culprits = currentBuild.rawBuild.culprits ?: []

	if (culprits) {
		footer += "\r\n"
		footer += "Culprits:\r\n"
		for (culprit in culprits) {
			footer += "- ${culprit.displayName}\r\n"
		}
	}

	return footer
}

boolean buildShouldPing(def currentBuild) {
	String threshold = env.DISCORD_PING_IF ?: 'FAILURE'

	try {
		return currentBuild.resultIsWorseOrEqualTo(threshold)
	} catch (Throwable e) {
		echo "Invalid DISCORD_PING_IF='${threshold}': ${e.class.simpleName}: ${e.message}"
		return true
	}
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