#!/usr/bin/env bash

# Automatically discover JDK
if [ -d "/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home" ]; then
    export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home"
    export PATH="$JAVA_HOME/bin:$PATH"
elif [ -z "$JAVA_HOME" ]; then
    DETECTED_JAVA=$(/usr/libexec/java_home 2>/dev/null || true)
    if [ -n "$DETECTED_JAVA" ]; then
        export JAVA_HOME="$DETECTED_JAVA"
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
fi

# Ensure compiled and up-to-date with latest Java sources
if [ ! -f "sbtv-player.jar" ] || [ -n "$(find src -name '*.java' -newer sbtv-player.jar 2>/dev/null)" ]; then
    echo "Building application with latest changes..."
    ./build.sh
fi

PORT=${1:-8080}

# Stop any previous instance so the new build binds cleanly to the port
pkill -f "sbtv-player.jar" 2>/dev/null || true
pkill -f "com.sbtv.Main" 2>/dev/null || true
sleep 0.5

echo "Starting SBTV Streaming Player on port $PORT..."
echo "Open your browser at: http://localhost:$PORT"

# Open browser on macOS after short delay
(sleep 1 && open "http://localhost:$PORT" 2>/dev/null || true) &

if [ -f "sbtv-player.jar" ]; then
    java -jar sbtv-player.jar --port "$PORT"
else
    java -cp bin com.sbtv.Main --port "$PORT"
fi
