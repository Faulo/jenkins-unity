def call(String path) {
	if (isUnix()) {
		return callShellStdout("realpath '${path}'");
	} else {
		return callShellStdout("(Resolve-Path '${path}').Path");
	}
}