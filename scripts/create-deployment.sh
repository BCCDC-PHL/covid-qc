#!/bin/bash

rm -r target/deploy/covid-qc 2> /dev/null
mkdir -p target/deploy/covid-qc
mkdir -p target/deploy/covid-qc/js

cp -r resources/public/css target/deploy/covid-qc
cp -r resources/public/images target/deploy/covid-qc
cp resources/public/favicon.ico target/deploy/covid-qc/favicon.ico
cp resources/public/js/main.js target/deploy/covid-qc/js/main.js
cp resources/public/index.html target/deploy/covid-qc/index.html
pushd target/deploy > /dev/null
tar -czf covid-qc.tar.gz covid-qc
# rm -r covid-qc
popd > /dev/null
