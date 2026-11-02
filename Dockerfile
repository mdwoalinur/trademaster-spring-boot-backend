
# ====================================
# Stage 1: Build Spring Boot Application
# ====================================

FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy Maven configuration
COPY pom.xml .

# Copy application source code
COPY src ./src

# Build executable Spring Boot JAR
RUN mvn -B -DskipTests clean package

# ====================================
# Stage 2: Run Spring Boot Application
# ====================================

FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy executable JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Render default port
EXPOSE 10000

# Start Spring Boot using Render's PORT
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=70 -Dserver.address=0.0.0.0 -Dserver.port=${PORT:-10000} -jar app.jar"]
