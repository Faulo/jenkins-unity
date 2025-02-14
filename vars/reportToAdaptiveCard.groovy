def void call(String webhookUrl, def currentBuild, String name) {

	def statusEmojiMap = [
		"SUCCESS"  : "✔️",
		"UNSTABLE" : "⚠️",
		"FAILURE"  : "⛔",
		"ABORTED"  : "♻️"
	]
	def statusEmoji = statusEmojiMap[currentBuild.currentResult] ?: "⁉️"

	def buildNumber = env.BUILD_NUMBER
	def buildUrl = env.BUILD_URL
	def jobUrl = buildUrl.replace("/${buildNumber}/", "/")

	def body = []

	def jobLink = "[${name}](${jobUrl})"

	body << [
		"type": "TextBlock",
		"size": "small",
		"text": jobLink
	]

	def buildLink = "[#${buildNumber}](${buildUrl})"

	body << [
		"type": "TextBlock",
		"size": "large",
		"text": "${statusEmoji} ${buildLink}: **${currentBuild.currentResult}**"
	]


	def mentionEntities = []

	if (currentBuild.rawBuild.culprits.size() > 0) {
		body << [
			"type": "TextBlock",
			"weight": "Bolder",
			"text": "Culprits:"
		]

		def culprits = ""

		for (culprit in currentBuild.rawBuild.culprits) {
			culprits += "- <at>${culprit.displayName}</at>\r"

			mentionEntities << [
				"type": "mention",
				"text": "<at>${culprit.displayName}</at>",
				"mentioned": ["id": culprit.displayName, "name": culprit.displayName]
			]
		}

		body << [
			"type": "TextBlock",
			"text": culprits
		]
	}

	def error = currentBuild.rawBuild.execution.causeOfFailure
	if (error) {
		body << [
			"type": "TextBlock",
			"weight": "Bolder",
			"text": "Cause of failure:"
		]
		body << [
			"type": "TextBlock",
			"text": "${error}"
		]
	}

	body << [
		"type": "TextBlock",
		"weight": "Bolder",
		"text": "Changes:"
	]
	if (currentBuild.changeSets.size() > 0) {
		def changelog = ""
		for (changeLogSet in currentBuild.changeSets) {
			for (entry in changeLogSet.items) {
				changelog += "- ${entry.msg}\r"
			}
		}
		body << [
			"type": "TextBlock",
			"text": changelog
		]
	} else {
		body << [
			"type": "TextBlock",
			"text": "No changes detected."
		]
	}

	def jsonPayload = [
		"type": "message",
		"summary": "${statusEmoji} ${currentBuild.currentResult}: ${name}",
		"attachments": [
			[
				"contentType": "application/vnd.microsoft.card.adaptive",
				"content": [
					"type": "AdaptiveCard",
					"body": body,
					"\$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
					"version": "1.0",
					"msteams": [
						"entities": mentionEntities
					]
				]
			]
		]
	]

	def jsonMessage = new String(writeJSON(returnText: true, json: jsonPayload).getBytes("UTF-8"), "UTF-8")

	def response = httpRequest(
			httpMode: 'POST',
			requestBody: jsonMessage,
			contentType: 'APPLICATION_JSON; charset=UTF-8',
			url: webhookUrl
			)

	echo "AdaptiveCard Response: ${response.content}"
}