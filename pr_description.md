💡 What:
Replaced `next(...)` inner loop searches for reacted and replied messages with an O(1) dictionary lookup by `message_id`.

🎯 Why:
The previous implementation iterated through all `messages` inside list comprehensions for every reaction and reply. This resulted in O(N^2) time complexity, creating a performance bottleneck when processing WhatsApp threads with many messages.

📊 Impact:
Reduces time complexity from O(N^2) to O(N). For threads with a high volume of reactions or replies, this significantly decreases CPU cycles and response times for fetching WhatsApp messages.

🔬 Measurement:
The optimization can be measured by profiling the `get_whatsapp_messages` function on a `CRM Lead` or `CRM Deal` with a long thread of messages and observing the reduced CPU execution time compared to the original O(N^2) approach.
