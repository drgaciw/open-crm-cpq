package com.opencrm.product.serving;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.product.bundle.ProductBundleController;
import com.opencrm.product.catalog.ProductController;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import tools.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;

/** Private conditional caching of current DTO semantics, not of volatile envelope timestamps. */
@RestControllerAdvice
public class CatalogCacheAdvice implements ResponseBodyAdvice<Object> {
    private final ObjectMapper mapper;

    public CatalogCacheAdvice(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> controller = returnType.getContainingClass();
        return ProductController.class.isAssignableFrom(controller)
                || ProductBundleController.class.isAssignableFrom(controller)
                || CatalogReadController.class.isAssignableFrom(controller);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)
                || !(response instanceof ServletServerHttpResponse servletResponse)
                || !(body instanceof ApiResponse<?> api) || !api.isSuccess()
                || (request.getMethod() != HttpMethod.GET && request.getMethod() != HttpMethod.HEAD)
                || servletResponse.getServletResponse().getStatus() < 200
                || servletResponse.getServletResponse().getStatus() >= 300) return body;

        Object data = api.getData() instanceof Page<?> page
                ? new PageContent(page.getContent(), page.getSort().toString()) : api.getData();
        String version = digest(new Content(data, api.getPagination()));
        String etag = "W/\"" + version + "\"";
        var headers = response.getHeaders();
        headers.setETag(etag);
        headers.set("X-Catalog-Version", version);
        headers.setCacheControl("private, no-cache");
        var vary = new LinkedHashSet<>(headers.getVary());
        vary.addAll(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.COOKIE, HttpHeaders.ACCEPT));
        headers.setVary(List.copyOf(vary));
        // This advice runs only after a successful read, so wildcard means an existing representation.
        // ServletWebRequest's wildcard comparison is limited to unsafe methods in this Spring version.
        if (request.getHeaders().getIfNoneMatch().contains("*")) {
            response.setStatusCode(HttpStatus.NOT_MODIFIED);
            return null;
        }
        // Spring handles weak comparison, lists, GET/HEAD and status semantics for actual tags.
        var conditional = new ServletWebRequest(servletRequest.getServletRequest(), servletResponse.getServletResponse());
        return conditional.checkNotModified(etag) ? null : body;
    }

    private String digest(Content content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(content)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private record Content(Object data, ApiResponse.PageInfo pagination) { }
    private record PageContent(List<?> content, String sort) { }
}
