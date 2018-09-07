== About ==

This is a small, special purpose, Java utility for for converting the issue metadata and markdown table in the tdwd/bdq TG2 issues (https://github.com/tdwg/bdq/labels/TG2) into csv for eaiser human consumption.  Issue metadata and the key/value pairs in the markdown table in the body of each issue are placed in columns in an output.csv file in a form suitable for conversion to fittness for use framework rdf and annotated stub java methods by the test-util.sh utility in kurator-ffdq.

== Building ==

Build with

    mvn install 

== Use ==

Obtain issues as json from github API:

    wget "https://api.github.com/repos/tdwg/bdq/issues?labels=TG2;CORE&per_page=100"  -O issuelist1.json
    wget "https://api.github.com/repos/tdwg/bdq/issues?labels=TG2;CORE&per_page=100&page=2" -O issuelist2.json

If more than one json file, combine, e.g. 

    jq -s 'flatten | group_by(.id) | map(reduce .[] as $x ({}; . * $x))' issuelist1.json issuelist2.json > issuelist.json

Then convert to csv with issueconverter jar

usage: java -jar issueconverter-0.0.1-SNAPSHOT-jar-with-dependencies.jar
 -f <arg>   JSON file to convert

    java -jar issueconverter-0.0.1-SNAPSHOT-jar-with-dependencies.jar -f issuelist.json 

Resulting output.csv should be in suitable form for input into kurator-ffdq utility test-util.sh, for example: 

    sh test-util.sh -config testsuite/testsuite.properties -format JSON-LD \
       -out testsuite/tg2_issues_2018Sept7.json \
       -in testsuite/tg2_issues_2018Sept7.csv

