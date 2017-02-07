#!/usr/bin/env bash

export KAFKA_ADVERTISED_HOST_NAME=$(hostname -i)
exec /usr/bin/start-kafka.sh