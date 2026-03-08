# Command 1: Git HEAD
Write-Output "=== Git HEAD ==="
git rev-parse HEAD
Write-Output ""

# Command 2: Customer.java class line
Write-Output "=== Customer.java class declaration ==="
(Get-Content "backend\services\pricing-ai-rules-service\src\main\java\com\opencrm\pricingairules\domain\model\Customer.java") | Select-String "class Customer" | ForEach-Object { $_.LineNumber }
Write-Output ""

# Command 3: PricingController ResponseEntity lines
Write-Output "=== PricingController ResponseEntity lines ==="
(Get-Content "backend\services\pricing-ai-rules-service\src\main\java\com\opencrm\pricingairules\api\controller\PricingController.java") | Select-String "ResponseEntity" | Select-Object -First 3 | ForEach-Object { "$($_.LineNumber): $($_.Line)" }
Write-Output ""

# Command 4: Security/Config files
Write-Output "=== Security and Config files ==="
Get-ChildItem -Recurse "backend\services\pricing-ai-rules-service\src\main\java" -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "*Security*" -or $_.Name -like "*Config*" } | Select-Object FullName
Write-Output ""

# Command 5: ParameterValue JsonSubTypes
Write-Output "=== ParameterValue.java JsonSubTypes and Param lines ==="
(Get-Content "backend\services\pricing-ai-rules-service\src\main\java\com\opencrm\pricingairules\ai\rl\model\ParameterValue.java") | Select-String "JsonSubTypes|NumericalParam|NumericParam" | ForEach-Object { "$($_.LineNumber): $($_.Line)" }
Write-Output ""

# Command 6: DRL setFinalPrice and salience
Write-Output "=== DRL setFinalPrice and salience lines ==="
(Get-Content "backend\services\pricing-ai-rules-service\src\main\resources\rules\legacy-additive-tier-and-floor.drl") | Select-String "setFinalPrice|salience" | ForEach-Object { "$($_.LineNumber): $($_.Line)" }
Write-Output ""

# Command 7: AIOrchestrationService conflicting price
Write-Output "=== AIOrchestrationService conflicting price line ==="
(Get-Content "backend\services\pricing-ai-rules-service\src\main\java\com\opencrm\pricingairules\ai\orchestration\AIOrchestrationService.java") | Select-String "conflicting price" | ForEach-Object { "$($_.LineNumber): $($_.Line)" }
