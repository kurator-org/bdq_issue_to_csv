#!/bin/bash

wget "https://api.github.com/repos/tdwg/bdq/issues?labels=TEST&per_page=100"  -O issuelist1.json
wget "https://api.github.com/repos/tdwg/bdq/issues?labels=TEST&per_page=100&page=2" -O issuelist2.json

jq -s 'flatten | group_by(.id) | map(reduce .[] as $x ({}; . * $x))' issuelist1.json issuelist2.json > issuelist.json

java -jar target/issueconverter-0.0.5-SNAPSHOT-jar-with-dependencies.jar -f issuelist.json

cp output.csv ../bdq/tg2/core/TG2_tests.csv

wget "https://api.github.com/repos/tdwg/bdq/issues?labels=Supplementary&per_page=100&state=all" -O supplementalissuelist.json

java -jar target/issueconverter-0.0.5-SNAPSHOT-jar-with-dependencies.jar -f supplementalissuelist.json

cp output.csv ../bdq/tg2/supplementary/TG2_supplementary_tests.csv
