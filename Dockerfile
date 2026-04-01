# Stage 1 — build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# download dependencies first — cached if pom.xml unchanged
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# Stage 2 — run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# create non-root user — security best practice
RUN addgroup -S wallet && adduser -S wallet -G wallet
USER wallet

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]