FROM eclipse-temurin:25-jre-noble
WORKDIR /app

COPY build/libs/*-SNAPSHOT.jar app.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
