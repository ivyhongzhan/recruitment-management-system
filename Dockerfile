FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/recruitment-management-system-2026.1.0.jar app.jar
EXPOSE 8000
ENTRYPOINT ["java","-jar","/app/app.jar"]
