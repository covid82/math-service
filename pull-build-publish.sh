#!/usr/bin/env sh
git pull
docker build -t math-service .
docker tag math-service:latest 489683348645.dkr.ecr.eu-west-1.amazonaws.com/math-service:latest
docker push 489683348645.dkr.ecr.eu-west-1.amazonaws.com/math-service:latest