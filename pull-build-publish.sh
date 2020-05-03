#!/usr/bin/env sh
git pull
set SBT_OPTS="-Xms512M -Xmx1024M -Xss2M -XX:MaxMetaspaceSize=1024M" sbt
sbt assembly
sudo aws ecr get-login-password --region eu-west-1 | sudo docker login --username AWS --password-stdin 489683348645.dkr.ecr.eu-west-1.amazonaws.com/math-service
docker build -t math-service .
docker tag math-service:latest 489683348645.dkr.ecr.eu-west-1.amazonaws.com/math-service:latest
docker push 489683348645.dkr.ecr.eu-west-1.amazonaws.com/math-service:latest