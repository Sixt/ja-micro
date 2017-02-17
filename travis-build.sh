#!/usr/bin/env bash

git config --global user.email "java-micro@sixt.com"
git config --global user.name "Travis CI"

TRAVIS_TAG=`git tag --contains`

if ([ -n "${TRAVIS_TAG}" ]); then
  echo "Running release build"
  ./gradlew release -Prelease.useAutomaticVersion=true -Prelease.releaseVersion="$TRAVIS_TAG" -x preTagCommit -x createReleaseTag -x checkCommitNeeded -x commitNewVersion
  git remote set-url origin https://${GITHUB_API_KEY}:github.com/Sixt/ja-micro.git
  git add gradle.properties
  git commit -m "Automatic version bump after release"
  git push origin
else
  echo "Running CI build"
  ./gradlew build
fi