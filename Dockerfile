# Build stage: compile and package with the Maven wrapper on JDK 21.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

# Run stage: JRE only.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/ledgerline-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
