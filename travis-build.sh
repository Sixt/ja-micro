#!/usr/bin/env bash

TRAVIS_TAG=`git tag --contains`

if ([ -n "${TRAVIS_TAG}" ]); then
  echo "Running release build"
  ./gradlew release -Prelease.useAutomaticVersion=true -Prelease.releaseVersion="$TRAVIS_TAG" -x preTagCommit -x createReleaseTag -x checkCommitNeeded
else
  echo "Running CI build"
  ./gradlew build
fi