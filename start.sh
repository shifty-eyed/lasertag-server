#!/bin/bash

# Define the main class
MAIN_CLASS="net.lasertag.lasertagserver.LasertagServerApplication"

# Build the classpath by including all jars from ./lib and ./target/classes
CLASSPATH="target/classes:$(find lib -name "*.jar" | tr '\n' ':')"

# Run the Java application
java -cp "$CLASSPATH" "$MAIN_CLASS"

