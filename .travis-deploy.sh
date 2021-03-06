#!/usr/bin/env bash
if [[ $JAVA_HOME == *"openjdk"* && $TRAVIS_BRANCH != "master" ]]; then
    echo "SNAPSHOT DEPLOYING"
    mvn -Ptravis-deploy-snapshot deploy --settings ./.travis.settings.xml -B -Dgpg.skip
else
    echo "SKIP DEPLOYING"
    mvn verify -B -Dgpg.skip
fi
