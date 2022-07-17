FROM openjdk:17-jdk-alpine

ARG JAR_FILE=./temperature-monitor.jar
ARG PROP_FILE=./config.properties

# cd /usr/local/runme
WORKDIR /usr/local/runme

COPY ${JAR_FILE} app.jar
COPY ${PROP_FILE} .

# java -jar /usr/local/runme/app.jar
ENTRYPOINT ["java","-jar","app.jar"]