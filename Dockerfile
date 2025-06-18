# Stage 1: Build the Spring Boot application
FROM maven:3-openjdk-21 AS builder

# Set the working directory in the builder stage
WORKDIR /app

# Copy the pom.xml and download dependencies first
# This improves build cache performance
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy all source code
COPY src ./src

# Package the application (skip tests for faster builds and if they are failing)
RUN mvn clean package -DskipTests

# Stage 2: Create the final lightweight image
FROM openjdk:21-slim-buster

# Set the working directory in the final image
WORKDIR /app

# Copy the JAR file from the builder stage
COPY --from=builder /app/target/cyclomatic-complexity-analyzer-0.0.1-SNAPSHOT.jar app.jar

# Expose the port your Spring Boot application listens on (default is 8080)
EXPOSE 8080

# Command to run your Spring Boot application when the container starts
ENTRYPOINT ["java", "-jar", "app.jar"]