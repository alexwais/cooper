#!/bin/bash

# Usage: sh ./runParameterized.sh a 10 GA

java -jar \
    -Djava.library.path=/Applications/CPLEX_Studio1210/cplex/bin/x86-64_osx \
    -Dscenario=$1 \
    -Dmultiplicator=$2 \
    -Dstrategy=$3 \
    target/Cooper.jar && \

echo "-- FINISHED $1@$2 - $3"