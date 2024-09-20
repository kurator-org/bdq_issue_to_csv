## About ##

This is a small, special purpose, Java utility for for converting the issue metadata and markdown table in the tdwd/bdq TG2 issues (https://github.com/tdwg/bdq/labels/TG2) into csv for eaiser human consumption.  Issue metadata and the key/value pairs in the markdown table in the body of each issue are placed in columns in an output.csv file in a form suitable for conversion to fittness for use framework rdf and annotated stub java methods by the test-util.sh utility in kurator-ffdq.

## Building ##

Build with

    mvn package

## Use ##

Obtain issues (with label=CORE https://github.com/tdwg/bdq/issues?q=is%3Aissue+label%3ACORE on web UI) as json from github API:

    wget "https://api.github.com/repos/tdwg/bdq/issues?labels=CORE&per_page=100"  -O issuelist1.json
    wget "https://api.github.com/repos/tdwg/bdq/issues?labels=CORE&per_page=100&page=2" -O issuelist2.json

If more than one json file, combine, e.g (using the jq command line JSON processor). 

    jq -s 'flatten | group_by(.id) | map(reduce .[] as $x ({}; . * $x))' issuelist1.json issuelist2.json > issuelist.json

Then convert to csv with issueconverter jar (produces a converted csv file in output.csv)

usage: java -jar issueconverter-0.0.5-SNAPSHOT-jar-with-dependencies.jar
 -f <arg>   JSON file to convert

    java -jar issueconverter-0.0.5-SNAPSHOT-jar-with-dependencies.jar -f issuelist.json 

The resulting output.csv (produced from issuelist.json) is the file which is checked in to tdwg/bdq as https://github.com/tdwg/bdq/blob/master/tg2/core/TG2_tests.csv

Java class anotations are shown on the console as issueconverter is running, if desired, you can capture these
java annotations for each test to a file:

    java -jar issueconverter-0.0.5-SNAPSHOT-jar-with-dependencies.jar -f issuelist.json  > annotations.java

The resulting output.csv should be in suitable form for input into kurator-ffdq utility test-util.sh to generate RDF, for example: 

    sh test-util.sh -config testsuite/testsuite.properties -format JSON-LD \
        -out testsuite/tg2_issues_2018Sept7.json \
        -in testsuite/tg2_issues_2018Sept7.csv

or, in more detail, using the csv copy of the tests in tdwg/bdq to generate the RDF/XML copy of the tests in tdwg/bdq (note, 
other than the test GUIDs, the uuid values in the generated RDF are not stable and will be replaced with each run of 
the test-util.sh utility in kurator-ffdq):

    cd ~/git
    git clone git@github.com:kurator-org/bdq_issue_to_csv.git
    cd bdq_issue_to_csv
    mvn package
    wget "https://api.github.com/repos/tdwg/bdq/issues?labels=CORE&per_page=100"  -O issuelist1.json
    wget "https://api.github.com/repos/tdwg/bdq/issues?labels=CORE&per_page=100&page=2" -O issuelist2.json
    jq -s 'flatten | group_by(.id) | map(reduce .[] as $x ({}; . * $x))' issuelist1.json issuelist2.json > issuelist.json
    java -jar ./target/issueconverter-0.0.5-SNAPSHOT-jar-with-dependencies.jar -f issuelist.json -u ../bdq/tg2/core/usecase_test_list.csv -l ../bdq/tg2/core/test_label_mappings.csv
    cd ~/git
    git clone git@github.com:tdwg/bdq.git
    cp bdq_issue_to_csv/output.csv bdq/tg2/core/TG2_tests.csv
    cp bdq_issue_to_csv/multirecord_measures.csv bdq/tg2/core/TG2_multirecord_measure_tests.csv
    git clone git@github.com:kurator-org/kurator-ffdq
    cd kurator-ffdq
    mvn package
    grep -v "AllAmendmentTestsRunOnSingleRecord" ../bdq/tg2/core/TG2_tests.csv  | grep -v "AllDarwin" > data/TG2_tests.csv
    cp ../bdq/tg2/core/TG2_multirecord_measure_tests.csv data/TG2_multirecord_measure_tests.csv
    ./test-util.sh -config data/tg2_tests.properties -format RDFXML -out ../bdq/tg2/core/TG2_tests.xml -in  data/TG2_tests.csv -guidFile ../bdq/tg2/core/TG2_tests_additional_guids.csv -useCaseFile ../bdq/tg2/core/usecase_test_list.csv -ieGuidFile ../bdq/tg2/core/information_element_guids.csv
    ./test-util.sh -config data/tg2_tests.properties -format RDFXML -out ../bdq/tg2/core/TG2_multirecord_measure_tests.xml -in  data/TG2_multirecord_measure_tests.csv -guidFile ../bdq/tg2/core/TG2_tests_additional_guids.csv -useCaseFile ../bdq/tg2/core/usecase_test_list.csv -ieGuidFile ../bdq/tg2/core/information_element_guids.csv

And for supplementary tests: 

    cd ../bdq_issue_to_csv
    wget "https://api.github.com/repos/tdwg/bdq/issues?labels=Supplementary&per_page=100&state=all" -O supplementalissuelist.json
    java -jar target/issueconverter-0.0.5-SNAPSHOT-jar-with-dependencies.jar -f supplementalissuelist.json
    cp ./output.csv ../bdq/tg2/supplementary/TG2_supplementary_tests.csv
    cd ../kurator-ffdq
    grep -v "AllDarwin" ../bdq/tg2/supplementary/TG2_supplementary_tests.csv  > data/TG2_supplementary_tests.csv
    ./test-util.sh -config data/tg2_tests.properties -format RDFXML -out ../bdq/tg2/supplementary/TG2_supplementary_tests.xml -in data/TG2_supplementary_tests.csv -ieGuidFile ../bdq/tg2/core/information_element_guids.csv

Note, values that do not fit the expections of the controlled vocabularies used in kurator-ffdq may cause rows to be skipped or may cause fatal exceptions in generating the RDF (AllDarwinCoreTerms as a composite information element isn't supported yet, so grep is used to exclude lines in the csv containing that value".
