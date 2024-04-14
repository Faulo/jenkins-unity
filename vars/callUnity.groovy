def String call(String body, String file = "") {
	if (CallUnityInitializer.initialize()) {
		callComposer('install --no-interaction --no-dev');
	}

	def result = callComposer("exec ${body}");

	if (file != "") {
		writeFile(file: file, text: result);
	}

	return result;
}
