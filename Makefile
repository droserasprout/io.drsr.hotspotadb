JAVA_HOME := /usr/lib/jvm/java-21-openjdk
ANDROID_SDK_ROOT := $(HOME)/Android/Sdk

unexport ANDROID_HOME

.PHONY: $(MAKECMDGOALS)

build:
	./gradlew assembleDebug

install:
	./gradlew installDebug

release:
	./gradlew assembleRelease

lint:
	./gradlew ktlintCheck detekt

clean:
	./gradlew clean
