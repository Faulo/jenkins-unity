def void call(String webhookUrl, def currentBuild, String name) {

	def statusEmojiMap = [
		"SUCCESS"  : "✔️",
		"UNSTABLE" : "⚠️",
		"FAILURE"  : "⛔",
		"ABORTED"  : "♻️"
	]
	def statusEmoji = statusEmojiMap[currentBuild.currentResult] ?: "⁉️"

	def buildNumber = env.BUILD_NUMBER
	def buildName = env.JOB_BASE_NAME
	def buildUrl = env.BUILD_URL
	def jobUrl = buildUrl.replace("/${buildNumber}/", "/")
	def testUrl = buildUrl + "testReport/"

	def body = []

	def jobLink = "[${name}](${jobUrl})"
	def testLink = "[tests](${testUrl})"

	body << [
		"type": "TextBlock",
		"size": "small",
		"text": jobLink
	]

	def buildLink = "[${buildName} #${buildNumber}](${buildUrl})"

	body << [
		"type": "TextBlock",
		"size": "large",
		"text": "${statusEmoji} ${buildLink}: **${currentBuild.currentResult}**"
	]

	def testResultAction = currentBuild.rawBuild.getAction(hudson.tasks.junit.TestResultAction)
	if (testResultAction) {
		def totalTests = testResultAction.totalCount
		def failedTests = testResultAction.failCount

		if (failedTests == 0) {
			body << [
				"type": "TextBlock",
				"color": "good",
				"text": "🎉 All ${totalTests} ${testLink} passed."
			]
		} else {
			body << [
				"type": "TextBlock",
				"color": "warning",
				"text": "☠️ Failed ${testLink}: **${failedTests}**"
			]
		}
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
		"summary": "${statusEmoji} ${name}: ${currentBuild.currentResult}",
		"attachments": [
			[
				"contentType": "application/vnd.microsoft.card.adaptive",
				"content": [
					"type": "AdaptiveCard",
					"body": body,
					"\$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
					"version": "1.2",
					"msteams": [
						"width": "Full",
						"entities": mentionEntities
					]
				]
			]
		]
	]

	def jsonMessage = writeJSON(returnText: true, json: jsonPayload)

	def response = httpRequest(
			httpMode: 'POST',
			requestBody: jsonMessage,
			contentType: 'APPLICATION_JSON_UTF8',
			url: webhookUrl
			)

	echo "AdaptiveCard Response: ${response.content}"
}