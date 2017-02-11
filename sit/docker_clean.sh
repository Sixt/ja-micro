#!/bin/bash

# set -o errexit

SERVICE=$1

echo "Cleaning up docker images of $1..."

unamestr=`uname`
if [[ "$unamestr" == 'Linux' ]]; then
	XARGS_CMD='xargs -r'
else
	XARGS_CMD='xargs'
fi

# Stop running docker containers
image_prefix=$(docker ps | grep ${SERVICE} | awk '!NF || !seen[$3]++' | awk '{print $2}' | awk -F_ '{print $1}')

if [[ ! -z "${image_prefix// }" ]]; then
    docker ps | grep "${image_prefix}_" | awk '{print $1}' | xargs -r docker stop
fi

# Remove docker images
docker_images=$(docker images | grep "${SERVICE}" | awk '!NF || !seen[$3]++' | awk '{print $3}')

if [[ ! -z "${docker_images// }" ]]; then
    echo $docker_images | ${XARGS_CMD} docker rmi -f
fi

exit 0
