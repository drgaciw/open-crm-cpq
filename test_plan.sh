#!/bin/bash
set -e

# Run tests in product-service
cd backend/services/product-service
mvn test
