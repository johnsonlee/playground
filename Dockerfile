FROM johnsonlee/gradle-8.3:springboot-3.3.4 AS builder
WORKDIR /app
ADD . .
RUN ./gradlew assemble --no-daemon

FROM ubuntu:latest

ENV ANDROID_SDK_ROOT="/usr/local/android-sdk"
ENV ANDROID_API_LEVEL="31"
ENV ANDROID_BUILD_TOOLS_VERSION="34.0.0"
ENV PATH="$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/tools/bin"
ENV CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-10406996_latest.zip"

RUN apt-get update && apt-get install -y openjdk-17-jdk-headless curl unzip && apt-get clean
RUN mkdir "$ANDROID_SDK_ROOT" ~/.android \
    && mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools" \
    && curl -o /tmp/cmdline-tools.zip $CMDLINE_TOOLS_URL \
    && unzip -d "$ANDROID_SDK_ROOT/cmdline-tools" /tmp/cmdline-tools.zip \
    && mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip \
    && yes | sdkmanager --licenses \
    && touch ~/.android/repositories.cfg \
    && sdkmanager --install "build-tools;$ANDROID_BUILD_TOOLS_VERSION" "platforms;android-$ANDROID_API_LEVEL" "platform-tools"

WORKDIR /app
COPY /data data
COPY /build/aars aars
COPY /build/libs/*.jar app.jar
CMD ["/bin/bash", "-c", "exec java $JAVA_OPTS -jar app.jar --spring.profiles.active=prod"]
