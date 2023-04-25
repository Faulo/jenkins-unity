def String call(String body) {
	if (!callUnity.called) {
		callUnity.called = true;
		callComposer('update');
	}
	return callComposer("exec ${body}");
}