# --- Stage 1: Build the Spring Boot application ---
# Use a Maven image to build the .jar file
FROM maven:3.8.5-openjdk-17 AS builder

# Set the working directory
WORKDIR /workspace

# Copy the pom.xml file and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the rest of the source code and build the application
COPY src ./src
RUN mvn package -DskipTests

# --- Stage 2: Create the final, lightweight image ---
# Use an OpenJDK image to run the application
FROM openjdk:17-slim

# Set the working directory inside the container
WORKDIR /app

# Copy the .jar file built in Stage 1
COPY --from=builder /workspace/target/*.jar app.jar

# Tell Docker to run this command when the container starts
ENTRYPOINT ["java", "-jar", "app.jar"]