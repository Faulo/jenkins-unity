def call(String body, String file = "") {
	if (CallUnityInitializer.initialize()) {
		callComposer('update --no-interaction --no-dev --optimize-autoloader --classmap-authoritative');
	}

	def result = callComposer("exec ${body}");

	if (file != "") {
		writeFile(file: file, text: result, encoding: "UTF-8");
	}

	return result;
}
