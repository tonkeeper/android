.PHONY: lock playstore_toolings

lock:
	./gradlew resolveAllDependencies prepareKotlinBuildScriptModel --write-locks --write-verification-metadata sha256

# Google Play release status from the terminal: ACTION=status posts the production release's state
# ("waiting for review", "20%", "20%, halted", "100%") to Slack; a release that is not live (draft, not
# sent for review, approved but not published, rejected) is printed, not posted. VERSION=<release name>
# picks a release other than the one rolling out or in review. Needs ANDROID_PUBLISHER_CREDENTIALS_PATH
# (the Play service account JSON or a gcloud user credential) and SLACK_BOT_TOKEN / SLACK_RELEASES_CHANNEL
# (env or the repository-root .env).
playstore_toolings:
	@../shared/scripts/playstore_toolings.py $(ACTION) \
		$(if $(VERSION),--version "$(VERSION)") \
		$(if $(PACKAGE),--package "$(PACKAGE)") \
		$(if $(YES),--yes) \
		$(if $(DRY_RUN),--dry-run)
