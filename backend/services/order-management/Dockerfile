FROM eclipse-temurin:25-jre-alpine

LABEL maintainer="OpenCRM Team"
LABEL description="Order Management Service"

WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY target/*.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8091
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
  CMD wget -qO- http://localhost:8091/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
