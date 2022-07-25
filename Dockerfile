FROM openjdk:17-jdk-alpine

# Set the timezone
RUN apk add --no-cache alpine-conf && \
    setup-timezone -z Asia/Shanghai

ARG JAR_FILE=./temperature-monitor-java.jar
ARG PROP_FILE=./config_docker_prod.properties

# cd /usr/local/runme
WORKDIR /usr/local/runme

COPY ${JAR_FILE} app.jar
COPY ${PROP_FILE} ./config.properties

# java -jar /usr/local/runme/app.jar
ENTRYPOINT ["java","-jar","app.jar"]