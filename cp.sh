#!/bin/bash

# Script that constructs the classpath from cp.txt


#CURR_DIR=$(pwd)

CODE_PATH="F:/research/software/code/eclipse/"
TABMAPPER_PATH=$CODE_PATH"tabmapper/"

# Source config.cfg
CONFIG_FILE="$TABMAPPER_PATH/config.cfg"
if [ -f "$CONFIG_FILE" ]; then
    source "$CONFIG_FILE"
else
    echo "Configuration file not found!"
    exit 1
fi


## Define the code path
#USER_CODE_PATH="$CODE_PATH"

# Initialise classpath
classpath=""

# Read each line from cp.txt, prepend CODE_PATH, 
# add semicolon, and append to classpath
while IFS= read -r line; do
    classpath+="$CODE_PATH$line;"
done < "$TABMAPPER_PATH""cp.txt"

# Remove any carriage returns
classpath=$(echo "$classpath" | tr -d '\r')

# Remove the trailing semicolon
classpath=${classpath%;}

# Output the constructed classpath
echo "$classpath"
