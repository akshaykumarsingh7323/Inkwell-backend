# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build

# Set working directory
WORKDIR /app

# Copy complete project
COPY . .

# Build Spring Boot jar
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre

# Set runtime directory
WORKDIR /app

# Copy generated jar
COPY --from=build /app/eureka-server/target/*.jar app.jar

# Expose application port
EXPOSE 8761

# Start application
ENTRYPOINT ["java","-jar","app.jar"]