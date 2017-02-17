#!/usr/bin/env bash

git config --global user.email "java-micro@sixt.com"
git config --global user.name "Travis CI"

TRAVIS_TAG=`git tag --contains`

if ([ -n "${TRAVIS_TAG}" ]); then
  echo "Running release build"
  ./gradlew build bintrayUpload
else
  echo "Running CI build"
  ./gradlew build
fi