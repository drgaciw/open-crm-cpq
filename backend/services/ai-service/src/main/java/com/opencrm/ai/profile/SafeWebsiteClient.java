package com.opencrm.ai.profile;

import org.springframework.stereotype.Component;
import javax.net.ssl.*;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.*;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** ADR-029 D-07. No proxy, redirects, cookies, credentials or second DNS resolution. */
@Component
public class SafeWebsiteClient {
    static final int MAX_BYTES = 262144;
    static final int MAX_TEXT = 8000;
    private static final int TIMEOUT_MS = 3000;
    private static final Semaphore SLOTS = new Semaphore(4);
    interface Resolver { InetAddress[] resolve(String host) throws IOException; }
    interface Transport { byte[] get(URI uri, InetAddress address) throws IOException; }
    private final Resolver resolver;
    private final Transport transport;
    public SafeWebsiteClient() { this(InetAddress::getAllByName, SafeWebsiteClient::fetchPinned); }
    SafeWebsiteClient(Resolver resolver, Transport transport) { this.resolver=resolver; this.transport=transport; }

    public String fetch(String url) {
        URI uri = validateUrl(url);
        if (!SLOTS.tryAcquire()) throw new IllegalArgumentException("website fetch busy");
        // Cap DNS as well as the full fetch. At most four stuck resolver calls can exist.
        var result = new CompletableFuture<String>();
        Thread.ofVirtual().start(() -> {
            try {
                InetAddress[] addresses = resolver.resolve(uri.getHost());
                if (addresses.length == 0) throw new IOException("no addresses");
                for (InetAddress address : addresses) requirePublic(address);
                byte[] body = transport.get(uri, addresses[0]);
                if (body.length > MAX_BYTES) throw new IOException("website too large");
                result.complete(plainText(new String(body, StandardCharsets.UTF_8)));
            } catch (Exception e) { result.completeExceptionally(e); }
            finally { SLOTS.release(); }
        });
        try { return result.get(8, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalArgumentException("website fetch interrupted"); }
        catch (ExecutionException | TimeoutException e) { throw new IllegalArgumentException("website rejected or unavailable"); }
    }
    static URI validateUrl(String url) {
        try {
            URI u = URI.create(url);
            if (url.length()>2048 || !"https".equalsIgnoreCase(u.getScheme()) || u.getHost()==null
                    || u.getRawUserInfo()!=null || u.getRawFragment()!=null || (u.getPort()!=-1 && u.getPort()!=443)
                    || url.chars().anyMatch(c -> c <= 32 || c == 127)) throw new IllegalArgumentException();
            return u;
        } catch (RuntimeException e) { throw new IllegalArgumentException("public HTTPS URL required"); }
    }
    static void requirePublic(InetAddress address) {
        byte[] b=address.getAddress(); int a=b[0]&255, c=b[1]&255;
        boolean denied=address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress();
        if (b.length==4) denied |= a==0 || a==10 || a==127 || a==169 && c==254 || a==172 && c>=16 && c<=31
                || a==192 && c==168 || a==100 && c>=64 && c<=127 || a>=224 || a==198 && (c==18 || c==19);
        // Only globally routed IPv6 unicast; excludes ULA, mapped/compatible, NAT64 and transition tunnels.
        else denied |= (a & 0xe0)!=0x20 || a==0x20 && c==0x02 || a==0x20 && c==0x01 && b[2]==0 && b[3]==0;
        if (denied) throw new IllegalArgumentException("non-public destination");
    }
    private static byte[] fetchPinned(URI uri, InetAddress address) throws IOException {
        try (Socket raw=new Socket()) {
            CompletableFuture.delayedExecutor(7,TimeUnit.SECONDS).execute(() -> {
                try { raw.close(); } catch(IOException ignored) { /* Deadline forcibly closes slow-drip streams. */ }
            });
            raw.connect(new InetSocketAddress(address,443),TIMEOUT_MS);
            raw.setSoTimeout(TIMEOUT_MS);
            try (SSLSocket tls=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault())
                    .createSocket(raw,uri.getHost(),443,true)) {
                SSLParameters parameters=tls.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS"); tls.setSSLParameters(parameters);
                tls.setSoTimeout(TIMEOUT_MS); tls.startHandshake();
                String path=uri.getRawPath(); if(path==null || path.isEmpty()) path="/";
                if(uri.getRawQuery()!=null) path+="?"+uri.getRawQuery();
                tls.getOutputStream().write(("GET "+path+" HTTP/1.0\r\nHost: "+uri.getHost()
                        +"\r\nAccept: text/html, text/plain\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                return readResponse(tls.getInputStream());
            }
        }
    }
    static byte[] readResponse(InputStream input) throws IOException {
        ByteArrayOutputStream header=new ByteArrayOutputStream(); int state=0;
        while(state<4) {
            int c=input.read(); if(c<0 || header.size()>=16384) throw new IOException("invalid headers");
            header.write(c);
            state = switch(state) { case 0 -> c=='\r'?1:0; case 1 -> c=='\n'?2:0;
                case 2 -> c=='\r'?3:0; default -> c=='\n'?4:0; };
        }
        String[] lines=header.toString(StandardCharsets.US_ASCII).split("\r\n");
        if(!lines[0].matches("HTTP/1\\.[01] 200(?: .*)?")) throw new IOException("status/redirect rejected");
        boolean type=false; long length=-1;
        for(int i=1;i<lines.length;i++) {
            int colon=lines[i].indexOf(':'); if(colon<1) throw new IOException("invalid header");
            String name=lines[i].substring(0,colon).toLowerCase(Locale.ROOT), value=lines[i].substring(colon+1).strip();
            if(name.equals("content-type")) type=value.toLowerCase(Locale.ROOT).matches("text/(html|plain)(;.*)?");
            if(name.equals("transfer-encoding") || name.equals("content-encoding") && !value.equalsIgnoreCase("identity"))
                throw new IOException("unsupported encoding");
            if(name.equals("content-length")) {
                try { length=Long.parseLong(value); } catch(NumberFormatException e) { throw new IOException("invalid length"); }
                if(length<0 || length>MAX_BYTES) throw new IOException("website too large");
            }
        }
        if(!type) throw new IOException("unsupported content type");
        byte[] body=input.readNBytes(MAX_BYTES+1);
        if(body.length>MAX_BYTES || length>=0 && body.length!=length) throw new IOException("invalid body length");
        return body;
    }
    static String plainText(String html) throws IOException {
        StringBuilder text=new StringBuilder();
        new ParserDelegator().parse(new StringReader(html),new HTMLEditorKit.ParserCallback() {
            int hidden;
            boolean ignored(HTML.Tag t) { return t==HTML.Tag.SCRIPT || t==HTML.Tag.STYLE || t==HTML.Tag.HEAD; }
            public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) { if(ignored(t)) hidden++; }
            public void handleEndTag(HTML.Tag t,int pos) { if(ignored(t) && hidden>0) hidden--; }
            public void handleText(char[] data,int pos) {
                if(hidden==0 && text.length()<MAX_TEXT) {
                    text.append(' '); text.append(data,0,Math.min(data.length,MAX_TEXT-text.length()));
                }
            }
        },true);
        return text.toString().replaceAll("[\\p{Cntrl}\\s]+"," ").strip();
    }
}
