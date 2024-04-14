class CallUnityInitializer {
	static boolean isInitialized = false;

	static boolean initialize() {
		if (!isInitialized) {
			isInitialized = true;
			return true;
		}
		return false;
	}
}

def String call(String body, String file = "") {
	if (CallUnityInitializer.initialize()) {
		callComposer('install --no-interaction --no-dev');
	}
	def result = callComposer("exec ${body}");
	
	if (file !="") {
		writeFile   (file:file, text:result);
	}
}
