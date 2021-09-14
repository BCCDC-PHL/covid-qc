#!/bin/bash

rm -r target/public/cljs-out/prod/*
clojure -M:fig -b prod
