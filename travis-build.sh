#!/usr/bin/env bash

if ([ ! -z "$TRAVIS_TAG" ]); then
  ./gradlew release -Prelease.useAutomaticVersion=true -Prelease.releaseVersion="$TRAVIS_TAG" -x preTagCommit -x createReleaseTag
else
  ./gradlew build
fi