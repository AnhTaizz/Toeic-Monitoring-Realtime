FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/server-0.1.0-SNAPSHOT.jar /app/server.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "/app/server.jar"]
