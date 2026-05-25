Wait! In `backend/services/crm-service/src/main/java/com/opencrm/crm/config/SecurityConfig.java`:
```java
        if (securityOpen) {
            builder.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            builder.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .requestMatchers("/api/v1/**").authenticated()
                    .anyRequest().authenticated()
            );
        }
```
Wait, if it's `!securityOpen`, it requires `.authenticated()`. But there is NO authentication mechanism configured! There is no `oauth2ResourceServer()`, no `httpBasic()`, no custom filter!
So all requests to `/api/v1/**` will be rejected with 403 Forbidden because there's no way to authenticate!

Wait, what about the Gateway service?
`backend/services/gateway-service/src/main/java/com/opencrm/gateway/config/SecurityConfig.java`
Let's check it.
