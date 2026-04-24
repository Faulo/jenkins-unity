def Boolean call() {
	return env?.OS?.toLowerCase()?.contains('windows') ?: false
}