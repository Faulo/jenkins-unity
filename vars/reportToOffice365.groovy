def call(String webhookUrl, def currentBuild, String name) {
	def status = "${currentBuild.currentResult}: ${name}"

	def message = ""
	for (changeLogSet in currentBuild.changeSets) {
		for (entry in changeLogSet.getItems()) {
			message += "- ${entry.msg}\r\n"
		}
	}

	office365ConnectorSend webhookUrl: webhookUrl, message: message, status: status
}