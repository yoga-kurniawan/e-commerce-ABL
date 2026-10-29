# Jalankan build dari root repository: docker build -f services/cart-service/Dockerfile .
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY services/ services/
# Normalkan akhir baris wrapper pada checkout Windows.
# Jalankan tests terpisah dengan database/profile pengujian sebelum rilis.
RUN sed -i 's/\r$//' mvnw && sh mvnw -B -ntp -pl services/cart-service -am -DskipTests package

FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app
COPY --from=build /workspace/services/cart-service/target/*.jar /app/app.jar
ENV SERVER_PORT=8080
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
