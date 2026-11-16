#!/usr/bin/env bash
set -e

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

echo "========================================================"
echo "    Building SBTV / MovieFlix M3U Player (Java)        "
echo "========================================================"
java -version
echo "--------------------------------------------------------"

mkdir -p bin
echo "Compiling Java source files..."
javac -d bin $(find src -name "*.java")

echo "Packaging standalone executable JAR (sbtv-player.jar)..."
cat << 'EOF' > bin/manifest.txt
Main-Class: com.sbtv.Main
EOF

jar cfm sbtv-player.jar bin/manifest.txt -C bin com

echo "--------------------------------------------------------"
echo "✓ Build successful! Executable created: sbtv-player.jar"
echo "✓ Run with: ./run.sh or java -jar sbtv-player.jar"
echo "========================================================"
