#!/bin/bash

# Script that installs tabmapper on your system. It must
# called from the same folder that folds config.cfg, cp.sh, 
# and cp.txt. 

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


remove_carriage_returns() {
    local file="$1"
    # Remove any carriage returns
    if grep -q $'\r' "$file"; then
        tr -d '\r' <"$file" >file.new && mv file.new "$file"
    fi
}


handle_file() {
    local file="$1"
    local make_executable="$2"
    # If the file exists, handle it
    if [ -f "$file" ]; then
        # Remove any carriage returns
        remove_carriage_returns "$file"

        # Make executable (if applicable)
        if [ "$make_executable" -eq 1 ]; then
            chmod +x "$file"
        fi
    # If not, return error
    else
        echo "File not found: $file."
        exit 1
    fi
}


echo "Installing ..."

# 1. Handle files
config_file="$(pwd)""/config.cfg"
handle_file "$config_file" 0
cp_file="cp.sh"
handle_file "$cp_file" 1
tm_file="tabmapper"
handle_file "$tm_file" 1

# 2. Source config.cfg to make CODE_PATH and PATH_PATH available locally
echo "... reading configuration file ... "
source "$config_file"
# Escape forward slashes in CODE_PATH
code_path_esc=$(echo "$CODE_PATH" | sed 's/\//\\\//g')
#path_path_esc=$(echo "$PATH_PATH" | sed 's/\//\\\//g')

# 3. Set CODE_PATH (replace placeholder with escaped CODE_PATH)
echo "... setting path ... "
placeholder="code_path_placeholder"
sed -i "s/$placeholder/$code_path_esc/g" "$cp_file"
sed -i "s/$placeholder/$code_path_esc/g" "$tm_file"        

# 4. Copy tabmapper to global environment
echo "... copying tabmapper to "$PATH_PATH" ..."
cp "$tm_file" "$PATH_PATH"

echo "... done!"