# Use a lightweight OpenJDK 21 base image
FROM openjdk:21-slim-buster

# Set the working directory inside the container
WORKDIR /app

# Copy the built Spring Boot JAR file from your local target directory
# IMPORTANT: Adjust 'cyclomatic-complexity-analyzer-0.0.1-SNAPSHOT.jar' to your actual JAR name
COPY target/cyclomatic-complexity-analyzer-0.0.1-SNAPSHOT.jar app.jar
# Expose the port that the Spring Boot application will run on
EXPOSE 8080

# Run the Spring Boot application when the container starts
ENTRYPOINT ["java", "-jar", "app.jar"]
# Optional: If you want to run the application with a specific profile, you can uncomment the following line
# ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
# Optional: If you want to run the application with specific JVM options, you can uncomment the following line
# ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]    
# Optional: If you want to run the application with a specific logging configuration, you can uncomment the following line
# ENTRYPOINT ["java", "-Dlogging.config=classpath:logback-spring.xml", "-jar", "app.jar"]