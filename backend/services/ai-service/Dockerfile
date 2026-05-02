FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="OpenCRM Team"
LABEL description="AI Agent Service — 37 CPQ agents via GLM-5 (Zhipu AI, ADR-002)"

WORKDIR /app

# Add non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8086

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8086/actuator/health || exit 1

# ⚠️ Data residency: Zhipu AI (GLM-5) routes traffic through Chinese infrastructure.
# Requires ZHIPUAI_API_KEY env var and DPA/compliance review before production use.
ENTRYPOINT ["java", \
  "-Xms256m", "-Xmx1g", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Dspring.profiles.active=docker", \
  "-jar", "app.jar"]
