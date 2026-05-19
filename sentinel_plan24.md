Wait, `backend/services/ai-service/src/main/resources/application.yml` has a default mock-key for `AZURE_OPENAI_API_KEY`:
`api-key: ${AZURE_OPENAI_API_KEY:mock-key}`

Wait, in the memory I saw this:
> To fix hardcoded credential vulnerabilities, replace default values in `docker-compose.yml` (e.g., `${VAR:-default}`) with bare environment variables (e.g., `${VAR}`) to force explicit configuration in the `.env` file.

Is there any default values in `backend/docker-compose.yml`?
