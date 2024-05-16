#!/bin/bash

# Script that constructs the classpath from cp.txt.

code_path="F:/research/software/code/eclipse/"
tabmapper_path="$code_path""tabmapper/"

# 1. Initialise classpath
classpath=""

# 2. Read each line from cp.txt, prepend code_path, 
# add semicolon, and append to classpath
while IFS= read -r line; do
    classpath+="$code_path""$line;"
done < "$tabmapper_path""cp.txt"

# 3. Remove any carriage returns; remove trailing semicolon
classpath=$(echo "$classpath" | tr -d '\r')
classpath=${classpath%;}

# 4. Return the constructed classpath
echo "$classpath"
