#!/bin/bash
# Gradle Wrapper script

GRADLE_VERSION=8.2
GRADLE_HOME="$HOME/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}-bin"

# دانلود Gradle اگر موجود نبود
if [ ! -d "$GRADLE_HOME" ]; then
    echo "Downloading Gradle ${GRADLE_VERSION}..."
    wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip
    unzip -q gradle-${GRADLE_VERSION}-bin.zip -d "$HOME/.gradle/wrapper/dists/"
    rm gradle-${GRADLE_VERSION}-bin.zip
fi

# اجرای Gradle
"$GRADLE_HOME/gradle-${GRADLE_VERSION}/bin/gradle" "$@"
