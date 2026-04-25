FROM --platform=$BUILDPLATFORM maven:3.9.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=builder /build/target/words.jar ./words.jar
COPY --from=builder /build/target/lib ./lib

ENV SERVICE_PORT=8080 \
    REDIS_HOST=redis \
    REDIS_PORT=6379

EXPOSE 8080
CMD ["java", "-jar", "/app/words.jar"]
