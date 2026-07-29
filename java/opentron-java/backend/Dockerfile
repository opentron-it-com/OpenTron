FROM maven:3.9-eclipse-temurin-21 as builder

WORKDIR /build

# Copy entire repo structure
COPY java/opentron-java/backend/pom.xml ./pom.xml
COPY java/opentron-java/backend/src ./src

# Build application (just Spring Boot repackage, skip shade plugin)
RUN mvn clean package -DskipTests \
    -Dmaven.javadoc.skip=true \
    -q

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy built jar
COPY --from=builder /build/target/opentron-java-backend-0.1.0-exec.jar app.jar

# Expose metrics port
EXPOSE 8080

# Run with standard GC settings
ENTRYPOINT [ \
  "java", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-XX:+ParallelRefProcEnabled", \
  "-Djdk.virtualThreadScheduler.parallelism=4", \
  "-jar", \
  "app.jar" \
]
