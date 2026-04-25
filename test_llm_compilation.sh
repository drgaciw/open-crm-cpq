#!/bin/bash
cd backend
mvn clean compile -DskipTests -pl services/ai-service
