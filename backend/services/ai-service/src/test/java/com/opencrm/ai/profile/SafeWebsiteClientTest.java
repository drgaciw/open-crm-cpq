package com.opencrm.ai.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SafeWebsiteClientTest {
    @ParameterizedTest @ValueSource(strings={"127.0.0.1","127.8.2.1","10.1.2.3","172.16.0.1","172.31.255.255",
        "192.168.1.2","169.254.169.254","169.254.1.1","100.64.0.1","100.127.255.255","0.0.0.0",
        "0.2.3.4","224.0.0.1","255.255.255.255","::1","::","fe80::1","fc00::1","fdff::1",
        "ff02::1","::ffff:127.0.0.1","::ffff:10.0.0.1","64:ff9b::a00:1","2002:0a00:0001::1","2001::1"})
    void rejectsNonPublicAddresses(String ip) throws Exception {
        assertThrows(IllegalArgumentException.class,()->SafeWebsiteClient.requirePublic(InetAddress.getByName(ip)));
    }
    @ParameterizedTest @ValueSource(strings={"http://example.com","file:///etc/passwd","https://user:pass@example.com",
        "https://example.com:8443","https://example.com/#fragment","https://example.com\r\nHost: bad","https:///missing"})
    void rejectsUnsafeUrls(String url) { assertThrows(IllegalArgumentException.class,()->SafeWebsiteClient.validateUrl(url)); }
    @ParameterizedTest @ValueSource(strings={"8.8.8.8","93.184.216.34","2606:4700:4700::1111"})
    void permitsPublicAddresses(String ip) throws Exception { SafeWebsiteClient.requirePublic(InetAddress.getByName(ip)); }
    @Test void mixedDnsAnswerIsRejectedBeforeTransport() {
        SafeWebsiteClient client=new SafeWebsiteClient(h->new InetAddress[]{InetAddress.getByName("8.8.8.8"),InetAddress.getLoopbackAddress()},
            (u,a)->{ fail("must not connect"); return null; });
        assertThrows(IllegalArgumentException.class,()->client.fetch("https://example.com"));
    }
    @Test void resolvesOnceAndPinsValidatedAddress() throws Exception {
        AtomicInteger count=new AtomicInteger(); InetAddress ip=InetAddress.getByName("8.8.8.8");
        SafeWebsiteClient client=new SafeWebsiteClient(h->{count.incrementAndGet(); return new InetAddress[]{ip};},
            (u,a)->{assertSame(ip,a); assertEquals("example.com",u.getHost()); return "<p>Useful &amp; safe</p>".getBytes(StandardCharsets.UTF_8);});
        assertEquals("Useful & safe",client.fetch("https://example.com")); assertEquals(1,count.get());
    }
    @ParameterizedTest @ValueSource(strings={"https://example.net/","https://127.0.0.1/","https://169.254.169.254/","/same-host"})
    void deniesAllRedirects(String location) {
        assertThrows(IOException.class,()->response("HTTP/1.1 302 Found\r\nLocation: "+location+"\r\n\r\n"));
    }
    @Test void rejectsOversizeHeadersBodiesAndDeclaredLength() {
        assertThrows(IOException.class,()->response("HTTP/1.0 200 OK\r\nX: "+"x".repeat(17000)+"\r\n\r\n"));
        assertThrows(IOException.class,()->response("HTTP/1.0 200 OK\r\nContent-Type: text/html\r\n\r\n"+"x".repeat(SafeWebsiteClient.MAX_BYTES+1)));
        assertThrows(IOException.class,()->response("HTTP/1.0 200 OK\r\nContent-Type: text/html\r\nContent-Length: 999999\r\n\r\n"));
    }
    @Test void rejectsUnsupportedEncodingAndBinary() {
        assertThrows(IOException.class,()->response("HTTP/1.0 200 OK\r\nContent-Type: application/pdf\r\n\r\n"));
        assertThrows(IOException.class,()->response("HTTP/1.0 200 OK\r\nContent-Type: text/html\r\nContent-Encoding: gzip\r\n\r\n"));
        assertThrows(IOException.class,()->response("HTTP/1.0 200 OK\r\nContent-Type: text/html\r\nTransfer-Encoding: chunked\r\n\r\n"));
    }
    @Test void bodyAndSanitizerAreBounded() throws Exception {
        assertEquals("abc",new String(response("HTTP/1.0 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 3\r\n\r\nabc"),StandardCharsets.UTF_8));
        assertEquals("Hello world",SafeWebsiteClient.plainText("<html><head><style>bad</style><script>evil()</script></head><body><p>Hello <b>world</b></p><script>evil()</script><!--hidden--></body></html>"));
        assertTrue(SafeWebsiteClient.plainText("<p>"+"x".repeat(20000)+"</p>").length()<=SafeWebsiteClient.MAX_TEXT);
    }
    @Test void ioTimeoutAndUnknownHostFailClosed() {
        var client=new SafeWebsiteClient(h->{throw new UnknownHostException();},(u,a)->new byte[0]);
        assertThrows(IllegalArgumentException.class,()->client.fetch("https://example.com"));
        var timeout=new SafeWebsiteClient(h->new InetAddress[]{InetAddress.getByName("8.8.8.8")},(u,a)->{throw new SocketTimeoutException();});
        assertThrows(IllegalArgumentException.class,()->timeout.fetch("https://example.com"));
    }
    private byte[] response(String value) throws IOException {
        return SafeWebsiteClient.readResponse(new ByteArrayInputStream(value.getBytes(StandardCharsets.US_ASCII)));
    }
}
