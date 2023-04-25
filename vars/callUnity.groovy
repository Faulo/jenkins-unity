def boolean callUnityInitialized = false;
def String call(String body) {
	if (!callUnityInitialized) {
		callUnityInitialized = true;
		callComposer('update');
	}
	return callComposer("exec ${body}");
}