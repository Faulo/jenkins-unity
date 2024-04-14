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