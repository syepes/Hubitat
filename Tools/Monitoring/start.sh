#!/bin/sh
mkdir -p data/grafana/ data/loki/ &> /dev/null
sudo chown -R 472:472 data/grafana &> /dev/null
sudo chown -R 10001:10001 data/loki &> /dev/null
docker-compose up -d