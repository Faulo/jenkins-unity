class CallUnity {
	static boolean isInitialized = false;

	static String call(String body) {
		if (!isInitialized) {
			isInitialized = true;
			callComposer('update');
		}
		return callComposer("exec ${body}");
	}
}

def String call(String body) {
	return CallUnity.call(body);
}