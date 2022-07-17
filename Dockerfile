FROM openjdk:17-jdk-alpine

# build jar file
RUN mvn clean package

ARG JAR_FILE=./target/temeprature-monitor-1.0-SNAPSHOT.jar
ARG PROP_FILE=./config.properties

# cd /usr/local/runme
WORKDIR /usr/local/runme

COPY ${JAR_FILE} app.jar
COPY ${PROP_FILE} .

# java -jar /usr/local/runme/app.jar
ENTRYPOINT ["java","-jar","app.jar"]