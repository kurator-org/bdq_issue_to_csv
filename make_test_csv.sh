#!/bin/bash
ok="false"
case $PWD/ in
	*/git/bdq_issue_to_csv*) ok="true";;
	*) echo "not in expected directory to start";;
esac
if [ $ok = "true" ]; then

	# wget "https://api.github.com/repos/tdwg/bdq/issues?labels=CORE&per_page=100"  -O issuelist1.json
	# wget "https://api.github.com/repos/tdwg/bdq/issues?labels=CORE&per_page=100&page=2" -O issuelist2.json
	# jq -s 'flatten | group_by(.id) | map(reduce .[] as $x ({}; . * $x))' issuelist1.json issuelist2.json > issuelist.json
	# java -jar ./target/issueconverter-1.0.1-SNAPSHOT-jar-with-dependencies.jar -f issuelist.json -u ../bdq/tg2/core/usecase_test_list.csv -l ../bdq/tg2/core/test_label_mappings.csv -g ../bdq/tg2/core/TG2_tests_additional_guids.csv -a ../bdq/tg2/core/TG2_tests_argument_guids.csv
	cd ../
	# cp bdq_issue_to_csv/output.csv bdq/tg2/core/TG2_tests.csv
	# cp bdq_issue_to_csv/multirecord_measures.csv bdq/tg2/core/TG2_multirecord_measure_tests.csv
	cd kurator-ffdq/
	grep -v "AllAmendmentTestsRunOnSingleRecord" ../bdq/tg2/core/TG2_tests.csv  | grep -v "AllDarwin" > data/TG2_tests.csv
	cp ../bdq/tg2/core/TG2_multirecord_measure_tests.csv data/TG2_multirecord_measure_tests.csv
	./test-util.sh -config data/tg2_tests.properties -format RDFXML -out ../bdq/tg2/core/TG2_tests.xml -in  data/TG2_tests.csv -guidFile ../bdq/tg2/core/TG2_tests_additional_guids.csv -useCaseFile ../bdq/tg2/core/usecase_test_list.csv -ieGuidFile ../bdq/tg2/core/information_element_guids.csv
	./test-util.sh -config data/tg2_tests.properties -format TURTLE -out ../bdq/tg2/core/TG2_tests.ttl -in  data/TG2_tests.csv -guidFile ../bdq/tg2/core/TG2_tests_additional_guids.csv -useCaseFile ../bdq/tg2/core/usecase_test_list.csv -ieGuidFile ../bdq/tg2/core/information_element_guids.csv
	./test-util.sh -config data/tg2_tests.properties -format JSON-LD -out ../bdq/tg2/core/TG2_tests.json -in  data/TG2_tests.csv -guidFile ../bdq/tg2/core/TG2_tests_additional_guids.csv -useCaseFile ../bdq/tg2/core/usecase_test_list.csv -ieGuidFile ../bdq/tg2/core/information_element_guids.csv
	./test-util.sh -config data/tg2_tests.properties -format RDFXML -out ../bdq/tg2/core/TG2_multirecord_measure_tests.xml -in  data/TG2_multirecord_measure_tests.csv -guidFile ../bdq/tg2/core/TG2_tests_additional_guids.csv -useCaseFile ../bdq/tg2/core/usecase_test_list.csv -ieGuidFile ../bdq/tg2/core/information_element_guids.csv
	./test-util.sh -config data/tg2_tests.properties -format TURTLE -out ../bdq/tg2/core/TG2_multirecord_measure_tests.ttl -in  data/TG2_multirecord_measure_tests.csv -guidFile ../bdq/tg2/core/TG2_tests_additional_guids.csv -useCaseFile ../bdq/tg2/core/usecase_test_list.csv -ieGuidFile ../bdq/tg2/core/information_element_guids.csv

    # Supplementary Tests
    cd ../bdq_issue_to_csv
    wget "https://api.github.com/repos/tdwg/bdq/issues?labels=Supplementary&per_page=100&state=all" -O supplementalissuelist.json
    java -jar target/issueconverter-1.2.0-jar-with-dependencies.jar -f supplementalissuelist.json
	# TODO: Needs a -g parameter for the supplementary tests to get the additional guids and a -a parameter for the argument guids
	# with files for the same to support stable guids on each build
    cp ./output.csv ../bdq/tg2/supplementary/TG2_supplementary_tests.csv
    cd ../kurator-ffdq
    grep -v "AllDarwin" ../bdq/tg2/supplementary/TG2_supplementary_tests.csv  | grep -v "AllAmendmentTestsRunOnSingleRecord" > data/TG2_supplementary_tests.csv
    ./test-util.sh -config data/tg2_tests.properties -format RDFXML -out ../bdq/tg2/supplementary/TG2_supplementary_tests.xml -in data/TG2_supplementary_tests.csv -ieGuidFile ../bdq/tg2/core/information_element_guids.csv -guidFile ../bdq/tg2/supplementary/TG2_supplementary_additional_guids.csv

fi
