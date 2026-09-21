.PHONY: lock

lock:
	./gradlew resolveAllDependencies prepareKotlinBuildScriptModel --write-locks --write-verification-metadata sha256
