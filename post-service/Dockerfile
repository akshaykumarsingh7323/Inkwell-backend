# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY . .

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/post-service/target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java","-jar","app.jar"]