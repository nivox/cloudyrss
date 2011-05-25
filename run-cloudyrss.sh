#!/bin/sh

# Path of the directory holding the jgrapes distribution (native libraries)
JGRAPES_DIST=../grapes/jgrapes/dist/
CONF_FILE=./conf.properties
#============================================================
# DON'T EDIT PAST THIS UNLESS YOU KNOW WHAT YOU'RE DOING
#============================================================

BUILD_DIR="./build"
LIB_DIRS="./lib ./lib/informa-deps"
if [ -z "$JGRAPES_DIST" ]; then
    echo "Missing configuration... Read the header of this script for help"
    exit 1
fi

CLASSPATH="$CLASSPATH:$BUILD_DIR"
for dir in $LIB_DIRS; do
    for jar in `ls $dir/*.jar`; do
        CLASSPATH+=:$jar
    done
done

JVMARGS="-Djava.library.path=$JGRAPES_DIST"
export LD_LIBRARY_PATH+=:$JGRAPES_DIST
java -cp $CLASSPATH $JVMARGS cloudyrss.CloudyRSSGui