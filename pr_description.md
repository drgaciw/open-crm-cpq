💡 What: Replaced individual database queries inside a loop with a batch query (`IN` clause) in `PriceBookInternalResolutionService.resolveBatch`.

🎯 Why: This resolves an N+1 query performance bottleneck that would cause significant delays when resolving prices for many line items simultaneously.

📊 Impact: Significantly reduces database round trips from O(N) to O(1), speeding up batch price resolution.

🔬 Measurement: Observe lower database latency and overall faster response times when executing batch pricing for multiple product items.
