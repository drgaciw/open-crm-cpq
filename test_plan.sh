#!/bin/bash
cd backend/services/product-service
../../mvnw clean compile test -Dtest=ConfigurationRuleServiceTest
