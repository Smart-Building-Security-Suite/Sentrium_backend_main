# Stage 1: Build the Maven application
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Build the application skipping tests
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime container
FROM eclipse-temurin:21-jre-jammy

# Hugging Face runs containers with UID 1000
RUN useradd -m -u 1000 user
USER user
ENV HOME=/home/user
WORKDIR $HOME/app

# Copy the built jar from the build stage
COPY --chown=user --from=build /app/target/*.jar app.jar

# Spring Boot will read the PORT env var provided by Hugging Face (default is 7860)
EXPOSE 7860

# Run the jar
CMD ["java", "-jar", "app.jar"]
