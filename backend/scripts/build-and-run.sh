#!/bin/bash
# Build all Maven modules and start the entire stack with Docker Compose.
# Usage: ./scripts/build-and-run.sh
#
# Prerequisites:
#   - Java 25 and Maven installed
#   - Docker and Docker Compose installed

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(dirname "$SCRIPT_DIR")"

echo "==> Building Maven modules (skipping tests)..."
cd "$BACKEND_DIR"
mvn clean package -DskipTests -q

echo "==> Starting Docker Compose stack..."
docker compose up --build -d

echo ""
echo "==> Stack is starting. Use 'docker compose logs -f' to follow logs."
echo "    Gateway:     http://localhost:8080"
echo "    CRM Service: http://localhost:8081"
echo "    Swagger UI:  http://localhost:8081/swagger-ui.html"
echo ""
echo "    To stop: docker compose down"
