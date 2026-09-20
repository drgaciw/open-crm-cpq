package com.opencrm.quote.snapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class QuoteRevisionConsumerTest {
    final ObjectMapper mapper=new ObjectMapper();
    final QuoteRevisionService service=mock(QuoteRevisionService.class);
    @Test void startupRequiresBrokerAuthenticationAclAttestationAndPrincipal() {
        assertThatThrownBy(()->new QuoteRevisionConsumer(mapper,service,false,"SASL_SSL","oms","pilot")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new QuoteRevisionConsumer(mapper,service,true,"PLAINTEXT","oms","pilot")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new QuoteRevisionConsumer(mapper,service,true,"SASL_SSL","","pilot")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new QuoteRevisionConsumer(mapper,service,true,"SASL_SSL","oms","")).isInstanceOf(IllegalStateException.class);
    }
    @Test void trustedListenerPreservesExactRevisionContract() throws Exception {
        var listener=new QuoteRevisionConsumer(mapper,service,true,"SASL_SSL","oms","pilot");
        var request=new QuoteRevisionRequest(1,"event","operation","order","b70be08b-f70d-406f-82db-96c3f53410dc",1,"pilot","account","correct");
        listener.accept(mapper.writeValueAsString(request));verify(service).reopen(request);
    }
}
