FROM eclipse-temurin:21-jre-alpine
ARG MODULE=store-service
WORKDIR /app
RUN addgroup -g 1001 -S appgroup && adduser -u 1001 -S appuser -G appgroup
COPY ${MODULE}/target/*.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
