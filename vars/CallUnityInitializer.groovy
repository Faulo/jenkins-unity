import java.util.HashSet
import java.util.Collections

class CallUnityInitializer {
	private static final Set<String> initializedAgents = Collections.synchronizedSet(new HashSet<String>())

	static boolean initialize(String agentName) {
		if (!agentName) {
			agentName = System.getenv('NODE_NAME') ?: 'UNKNOWN'
		}

		return initializedAgents.add(agentName)
	}
}