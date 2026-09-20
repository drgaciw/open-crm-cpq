package com.opencrm.contract.event;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OmsBrokerTrustTest {
    @Test void enabledConsumersFailClosedWithoutBrokerPrerequisites() {
        assertThrows(IllegalStateException.class,()->new OmsBrokerTrust(true,false,false,"SASL_SSL","quote","oms","pilot"));
        assertThrows(IllegalStateException.class,()->new OmsBrokerTrust(true,false,true,"PLAINTEXT","quote","oms","pilot"));
        assertThrows(IllegalStateException.class,()->new OmsBrokerTrust(true,true,true,"SASL_SSL","quote","","pilot"));
        assertThrows(IllegalStateException.class,()->new OmsBrokerTrust(false,true,true,"SASL_SSL","","oms","pilot"));
        assertThrows(IllegalStateException.class,()->new OmsBrokerTrust(true,true,true,"SASL_SSL","quote","oms",""));
    }
    @Test void disabledDefaultsBootButClaimsStillNeedAllowedScope() {
        var disabled=new OmsBrokerTrust(false,false,false,"","","","");
        assertThrows(IllegalArgumentException.class,()->disabled.requireScope("pilot"));
        var ready=new OmsBrokerTrust(true,true,true,"SASL_SSL","quote","oms","pilot");
        assertDoesNotThrow(()->ready.requireScope("pilot"));
        assertThrows(IllegalArgumentException.class,()->ready.requireScope("other"));
    }
}
