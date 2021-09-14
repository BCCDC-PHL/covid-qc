#!/bin/bash

rm -r target/deploy/covid-qc 2> /dev/null
mkdir -p target/deploy/covid-qc
mkdir -p target/deploy/covid-qc/js
cp -r resources/public/css target/deploy/covid-qc
cp -r resources/public/images target/deploy/covid-qc
cp target/public/cljs-out/prod/main_bundle.js target/deploy/covid-qc/js/main.js
cp resources/public/index_prod.html target/deploy/covid-qc/index.html
pushd target/deploy > /dev/null
tar -czf covid-qc.tar.gz covid-qc
rm -r covid-qc
popd > /dev/null
