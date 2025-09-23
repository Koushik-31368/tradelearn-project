# Use an official lightweight Java 17 runtime as a parent image
FROM openjdk:17-slim

# Set the working directory inside the container to /app
WORKDIR /app

# Copy the packaged .jar file from your project's "target" directory into the container
# It renames the file to app.jar for simplicity
COPY target/*.jar app.jar

# Tell Docker to run this command when the container starts
ENTRYPOINT ["java", "-jar", "app.jar"]