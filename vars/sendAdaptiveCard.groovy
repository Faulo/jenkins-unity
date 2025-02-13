def String call(String webhookUrl, Object currentBuild) {
	def jobName = env.JOB_NAME.split("/")[-1]
	def buildNumber = env.BUILD_NUMBER
	def buildUrl = env.BUILD_URL
	def jobUrl = buildUrl.replace("/${buildNumber}/", "/")

	def body = []

	def jobLink = "[${jobName}](${jobUrl})"
	def buildLink = "[#${buildNumber}](${buildUrl})"
	def statusText = "${jobLink} ${buildLink}: ${currentBuild.currentResult}"

	body << [
		"type": "TextBlock",
		"size": "Large",
		"weight": "Bolder",
		"text": statusText
	]

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

	def mentionEntities = []

	if (currentBuild.rawBuild.culprits.size() > 0) {
		body << [
			"type": "TextBlock",
			"weight": "Bolder",
			"text": "Culprits:"
		]

		def culprits = ""

		for (culprit in currentBuild.rawBuild.culprits) {
			culprits += "- <at>${culprit.email}</at>\r"

			mentionEntities << [
				"type": "mention",
				"text": "<at>${culprit.email}</at>",
				"mentioned": ["id": culprit.email, "name": culprit.displayName]
			]
		}

		body << [
			"type": "TextBlock",
			"text": culprits
		]
	}

	def jsonPayload = [
		"type": "message",
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

	def jsonMessage = groovy.json.JsonOutput.toJson(jsonPayload)

	def response = httpRequest(
			httpMode: 'POST',
			requestBody: jsonMessage,
			contentType: 'APPLICATION_JSON',
			url: webhookUrl
			)

	echo "AdaptiveCard Response: ${response.content}"
}