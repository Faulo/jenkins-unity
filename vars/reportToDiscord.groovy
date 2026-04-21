def call(String webhookUrl, def currentBuild, String name) {
	def description = "${currentBuild.currentResult}: ${name}"

	def footer = ""
	for (changeLogSet in currentBuild.changeSets) {
		for (entry in changeLogSet.getItems()) {
			footer += "- ${entry.msg}\r\n"
		}
	}

	catchError(message: 'Discord notification failed or timed out', buildResult: 'SUCCESS', stageResult: 'SUCCESS', catchInterruptions: true) {
		timeout(time: 30, unit: 'SECONDS') {
			discordSend description: description, footer: footer, link: env.BUILD_URL, result: currentBuild.currentResult, title: name, webhookURL: webhookUrl
		}
	}
}