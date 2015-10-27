#!/bin/sh

if [ $# -lt 3 ];then 
	echo "Usage debug.sh HOST PORT QUERY" 
fi 
if [ $# -gt 3 ];then
	joinQuery=$4
	PARAMS="$PARAMS joinQuery=$joinQuery" 
fi

host=$1
port=$2
query=$3
joinQuery=$4

PARAMS=" $PARAMS pollInterval=1 pollCount=1 *.group1.hosts=$host *.group1.label=ll_ query=$query  *.group1.ports=$port  devMode=true"
PARAMS=`echo $PARAMS | sed -e 's/;/\\;/g'`
echo "Connecting to [$host:$port]" 
echo "Executing query: $query" 

echo $PARAMS

which groovy
if [ $? -lt 1 ]; then 
	groovy -cp ./lib/helpers.jar javaapp.groovy   $PARAMS
else
	echo java -cp ./lib/helpers.jar:../../lib/groovy-all-1.8.7.jar groovy.lang.GroovyShell javaapp.groovy $PARAMS
	java -cp ./lib/helpers.jar:../../lib/groovy-all-1.8.7.jar groovy.lang.GroovyShell javaapp.groovy $PARAMS

fi

