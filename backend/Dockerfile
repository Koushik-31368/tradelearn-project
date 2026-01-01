# Use Java 17
FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy Maven wrapper and pom
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Download dependencies
RUN ./mvnw dependency:go-offline

# Copy source code
COPY src ./src

# Build the app
RUN ./mvnw clean package -DskipTests

# Run the app
CMD ["java", "-jar", "target/*.jar"]
