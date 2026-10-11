FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
# Cache dependencies separately - don't fail if offline
RUN mvn dependency:resolve -B || true
COPY src ./src
RUN mvn package -Dmaven.test.skip=true -B

FROM eclipse-temurin:25-jammy
RUN apt-get update && apt-get install -y tesseract-ocr tesseract-ocr-deu tesseract-ocr-eng && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY testdaten/fachwissen_seed.json ./testdaten/fachwissen_seed.json
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java","-jar","app.jar"]
