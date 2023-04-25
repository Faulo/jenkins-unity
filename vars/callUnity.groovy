class CallUnity {
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
	if (CallUnity.initialize()) {
		callComposer('update');
	}
	return callComposer("exec ${body}");
}