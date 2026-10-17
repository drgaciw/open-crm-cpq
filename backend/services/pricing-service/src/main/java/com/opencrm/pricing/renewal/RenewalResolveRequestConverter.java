package com.opencrm.pricing.renewal;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Reads the resolve body with unknown properties rejected. The service-wide Jackson mapper does not.
 */
@Component
public class RenewalResolveRequestConverter extends AbstractHttpMessageConverter<RenewalResolveRequest> {

    private static final ObjectMapper STRICT = new ObjectMapper()
            .findAndRegisterModules()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public RenewalResolveRequestConverter() {
        super(MediaType.APPLICATION_JSON);
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return RenewalResolveRequest.class.equals(clazz);
    }

    @Override
    protected RenewalResolveRequest readInternal(Class<? extends RenewalResolveRequest> clazz, HttpInputMessage inputMessage)
            throws IOException {
        byte[] body = inputMessage.getBody().readNBytes(RenewalResolveLimits.MAX_BODY_BYTES + 1);
        if (body.length > RenewalResolveLimits.MAX_BODY_BYTES) {
            throw new HttpMessageNotReadableException("Request body exceeds " + RenewalResolveLimits.MAX_BODY_BYTES + " bytes", inputMessage);
        }
        try {
            return STRICT.readValue(body, RenewalResolveRequest.class);
        } catch (IOException ex) {
            throw new HttpMessageNotReadableException("Malformed renewal resolve request", ex, inputMessage);
        }
    }

    @Override
    protected void writeInternal(RenewalResolveRequest request, org.springframework.http.HttpOutputMessage outputMessage) {
        throw new UnsupportedOperationException("Renewal resolve requests are not written");
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return false;
    }
}
