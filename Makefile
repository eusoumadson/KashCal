.PHONY: release dry-run test install

release:
	python3 app/src/fork/tools/release.py

dry-run:
	python3 app/src/fork/tools/release.py --dry-run

test:
	./gradlew testDebugUnitTest --no-daemon

install:
	./gradlew installDebug
