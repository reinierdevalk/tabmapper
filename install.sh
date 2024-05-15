#!/bin/bash

# Script that installs tabmapper on your system. It must
# called from the same folder that folds config.cfg. 

# NB: All line endings in a script must be unix style, so that 
# calling the script in bash does not lead to the following error:
# 
# ./my_script.sh: line <n>: $'\r': command not found
#
# There are two solutions to this. 
# 1. (Temporary) Call the following command before calling the script:
# 
# $ tr -d '\r' <my_script.sh >my_script.new && mv my_script.new my_script.sh
#
# 2. (Permanent) Open the file in Sublime 4, go to Preferences > Settings, 
# and add the following in the right field:
#
# {
#    "default_line_ending": "unix"
# }


PLACEHOLDER="code_path_placeholder"

echo "Installing ..."

# 1. Handle config.cfg
echo "... reading configuration file ... "
CONFIG_FILE="$(pwd)/config.cfg"
# If config.cgf exists
if [ -f "$CONFIG_FILE" ]; then
    # Remove any carriage returns
    if grep -q $'\r' "$CONFIG_FILE"; then
        tr -d '\r' <"$CONFIG_FILE" >config.new && mv config.new "$CONFIG_FILE"
    fi

    # Source config.cfg (make its variables CODE_PATH and PATH_PATH available here)
    source "$CONFIG_FILE"
else
    echo "Configuration file not found!"
    exit 1
fi

echo "... setting paths ... "

#echo $CODE_PATH
CODE_PATH_CLN=$CODE_PATH 
# Escape forward slashes in CODE_PATH
CODE_PATH_ESC=$(echo "$CODE_PATH" | sed 's/\//\\\//g')
#echo $CODE_PATH_ESC


# 2. Handle cp.sh
# Set code path, i.e., replace placeholder with escaped CODE_PATH
FILE="cp.sh"
#REPLACEMENT=$(printf '%s\n' "$CODE_PATH" | sed 's/[&/]/\\&/g')
#sed -i "s/$PLACEHOLDER/$REPLACEMENT/g" "$FILE"
sed -i "s/$PLACEHOLDER/$CODE_PATH_ESC/g" "$FILE"

# Remove any carriage returns
if grep -q $'\r' "$FILE"; then
    tr -d '\r' <"$FILE" >cp.new && mv cp.new "$FILE"
fi

# Make executable
chmod +x "$FILE"


# 3. Handle tabmapper
echo "... copying tabmapper to ""$PATH_PATH"" ..."
# Set code path, i.e., replace placeholder with escaped CODE_PATH
FILE="tabmapper"
#REPLACEMENT=$(printf '%s\n' "$CODE_PATH" | sed 's/[&/]/\\&/g')
#sed -i "s/$PLACEHOLDER/$REPLACEMENT/g" "$FILE"
sed -i "s/$PLACEHOLDER/$CODE_PATH_ESC/g" "$FILE"

# Remove any carriage returns
if grep -q $'\r' "$FILE"; then
    tr -d '\r' <"$FILE" >tabmapper.new && mv tabmapper.new "$FILE"
fi

# Make executable
chmod +x "$FILE"

# Copy to global environment
cp "$FILE" "$PATH_PATH"


echo "... done!"