FROM openjdk:17-jdk-alpine
ADD target/uploadService.jar uploadService.jar
ADD target/credentials.properties credsFile/credentials.properties

# Install Git
RUN apk update && apk add --no-cache git
EXPOSE 8080
ENV CREDS_VAL="credsFile/"
ENTRYPOINT ["java", "-jar", "uploadService.jar"]