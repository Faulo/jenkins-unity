class CallUnityInitializer {
    private static final Set<String> initializedAgents = Collections.synchronizedSet(new HashSet<String>())

    static boolean initialize(String executionIdentity) {
        if (!executionIdentity) {
            executionIdentity = System.getenv('NODE_NAME') ?: 'UNKNOWN'
        }

        return initializedAgents.add(executionIdentity)
    }
}
