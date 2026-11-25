# syntax=docker/dockerfile:1.7
# Jalankan build dari root repository: docker build -f services/cart-service/Dockerfile .
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY services/security-support/pom.xml services/security-support/
COPY services/product-service/pom.xml services/product-service/
COPY services/cart-service/pom.xml services/cart-service/
COPY services/order-service/pom.xml services/order-service/
COPY services/payment-service/pom.xml services/payment-service/
# Normalkan akhir baris wrapper pada checkout Windows dan cache dependensi.
RUN sed -i 's/\r$//' mvnw
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -ntp -pl services/cart-service -am dependency:go-offline

COPY services/security-support/src services/security-support/src
COPY services/cart-service/src services/cart-service/src
# Jalankan tests terpisah dengan database/profile pengujian sebelum rilis.
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -ntp -pl services/cart-service -am -DskipTests package

FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app
COPY --from=build /workspace/services/cart-service/target/cart-service-0.2.0-SNAPSHOT.jar /app/app.jar
ENV SERVER_PORT=8080
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
