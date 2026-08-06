# Stage 1: Build the Maven application
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Build the application skipping tests
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime container
FROM eclipse-temurin:21-jre-jammy

# Create non-root user
RUN useradd -m -u 1000 appuser
USER appuser
WORKDIR /home/appuser/app

# Copy the built jar from the build stage
COPY --chown=appuser --from=build /app/target/*.jar app.jar

# Create reports directory
RUN mkdir -p /tmp/reports

# Render provides PORT environment variable
EXPOSE 8080

# Run the jar with production settings
CMD ["java", \
     "-XX:MaxRAMPercentage=75.0", \
     "-XX:+UseContainerSupport", \
     "-Djava.security.egd=file:/dev/./urandom", \
     "-jar", "app.jar"]
