#!/bin/bash
# Script to create a table suitable to host a MySQL cloud bucket for use with CloudyRSS
# Usage:
#   create-mysqlcloud.sh -h mysql-user -h mysql-host -d mysql-db <bucket1> <bucket2> ...
#
# You will be asked for mysql password as many times as the number of buckets you are creating

MYSQL_CLOUD_TABLE_DEFINITION="cloud_key VARCHAR(500) PRIMARY KEY,
cloud_value BLOB,
cloud_timestamp INT UNSIGNED,
cloud_content_md5 VARCHAR(32),
cloud_content_length INT UNSIGNED,
cloud_content_type VARCHAR(128),
counter INT UNSIGNED"

MYSQL_HOST="localhost"
MYSQL_USER=$USER
MYSQL_DB=""
MYSQL_BUCKETS=""

while getopts "h:u:d:" opt; do
    case $opt in
        h) MYSQL_HOST=$OPTARG;;
        u) MYSQL_USER=$OPTARG;;
        p) MYSQL_PASS=$OPTARG;;
        d) MYSQL_DB=$OPTARG;;
        \?) echo "Invalid option: -$OPTARG"
            exit 1
            ;;
  esac
done
shift $((OPTIND-1))
MYSQL_BUCKETS=$@

if [ "$MYSQL_HOST." == "." ]; then
    echo "Null MySQL host"
    exit 1
fi

if [ "$MYSQL_USER." == "." ]; then
    echo "Null MySQL user"
    exit 1
fi

if [ "$MYSQL_DB." == "." ]; then
    echo "Null MySQL database"
    exit 1
fi

if [ "$MYSQL_BUCKETS." == "." ]; then
    echo "No bucket name specified"
    exit 1
fi

echo "Create these buckets in datatbase $MYSQL_DB?"
for b in $MYSQL_BUCKETS; do
    echo -e "\t$b"
done

read -p "Are you sure? " -n 1
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]
then
    exit 1
fi

for b in $MYSQL_BUCKETS; do
    mysql -u $MYSQL_USER -h $MYSQL_HOST -p -D $MYSQL_DB -e "CREATE TABLE $b ($MYSQL_CLOUD_TABLE_DEFINITION)"
done
