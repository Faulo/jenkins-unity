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

def String call(String body) {
	if (CallUnityInitializer.initialize()) {
		callComposer('update --no-interaction --no-dev');
	}
	return callComposer("exec ${body}");
}