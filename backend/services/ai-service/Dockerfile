FROM eclipse-temurin:25-jre-alpine

LABEL maintainer="OpenCRM Team"
LABEL description="AI Agent Service — Azure baseline with gated Jev/OpenRouter selection (ADR-014)"

WORKDIR /app

# Add non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8090

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:${MANAGEMENT_SERVER_PORT:-8090}/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-Xms256m", "-Xmx1g", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Dspring.profiles.active=docker", \
  "-jar", "app.jar"]
