package com.oriole.wisepen.docs.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oriole.wisepen.common.core.constant.CommonConstants;
import com.oriole.wisepen.common.core.constant.SecurityConstants;
import com.oriole.wisepen.common.core.context.GrayContextHolder;
import com.oriole.wisepen.docs.config.DocsServiceProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

@Tag(name = "文档聚合", description = "代理各微服务的 OpenAPI 与 AsyncAPI 文档")
@RestController
@RequiredArgsConstructor
public class OpenApiProxyController {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "content-length",
            "host",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade"
    );

    private final LoadBalancerClient loadBalancerClient;
    private final DocsServiceProperties docsServiceProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate(new JdkClientHttpRequestFactory());

    @Value("${wisepen.security.from-source:APISIX-wX0iR6tY}")
    private String fromSource;

    @Operation(
            summary = "获取微服务 OpenAPI 文档",
            description = """
                    - 用途：统一文档入口按服务键代理获取目标微服务的 OpenAPI JSON。
                    - 请求：serviceKey 为文档配置中的服务键。
                    - 约束：serviceKey 必须配置到实际服务名；目标服务必须有可用实例。
                    - 处理：通过负载均衡选择服务实例，转发内部来源标识和灰度开发者标识到目标服务的 OpenAPI 路径；不合并或改写目标 OpenAPI 内容。
                    - 失败：服务键未知、目标服务无实例或代理请求失败时按 HTTP 状态返回。
                    - 响应：返回目标微服务原始 OpenAPI JSON。
                    """
    )
    @GetMapping(value = "/docs/api/{serviceKey}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getOpenApi(@PathVariable String serviceKey, HttpServletRequest request) {
        String serviceName = resolveServiceName(serviceKey);
        URI uri = buildServiceUri(serviceName, resolveApiDocsPath(serviceKey), null);

        HttpHeaders headers = buildInternalHeaders(new HttpHeaders());
        ResponseEntity<String> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        String body = response.getBody();
        String rewrittenBody = body == null ? null : rewriteOpenApi(body, serviceKey, request);
        return ResponseEntity.status(response.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(rewrittenBody);
    }

    @Operation(
            summary = "获取微服务 AsyncAPI 文档",
            description = """
                    - 用途：统一文档入口按服务键代理获取目标微服务的 AsyncAPI JSON。
                    - 请求：serviceKey 为文档配置中的服务键。
                    - 约束：serviceKey 必须配置到实际服务名；目标服务必须有可用实例。
                    - 处理：通过负载均衡选择服务实例，转发内部来源标识和灰度开发者标识到目标服务的 Springwolf 文档路径；不改写 AsyncAPI 内容。
                    - 失败：服务键未知、目标服务无实例或代理请求失败时按 HTTP 状态返回。
                    - 响应：返回目标微服务原始 AsyncAPI JSON。
                    """
    )
    @GetMapping(value = "/docs/asyncapi/{serviceKey}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAsyncApi(@PathVariable String serviceKey) {
        String serviceName = resolveServiceName(serviceKey);
        URI uri = buildServiceUri(serviceName, resolveAsyncapiDocsPath(serviceKey), null);

        HttpHeaders headers = buildInternalHeaders(new HttpHeaders());
        ResponseEntity<String> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        return ResponseEntity.status(response.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.getBody());
    }

    // 代理 Scalar 发出的实际 API 请求
    // 让 Scalar 页面始终请求 docs-service 同源地址，避免浏览器直接跨源访问各个微服务导致 CORS 问题
    @RequestMapping("${wisepen.docs.proxy-base-path:/docs/proxy}/{serviceKey}/**")
    public ResponseEntity<byte[]> proxyApi(@PathVariable String serviceKey, HttpServletRequest request) throws IOException {
        String serviceName = resolveServiceName(serviceKey);
        String proxyPath = extractProxyPath(serviceKey, request);
        URI uri = buildServiceUri(serviceName, proxyPath, request.getQueryString());

        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        HttpHeaders headers = copyRequestHeaders(request);
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());

        ResponseEntity<byte[]> response = restTemplate.exchange(
                uri,
                method,
                new HttpEntity<>(body, headers),
                byte[].class
        );

        return ResponseEntity.status(response.getStatusCode())
                .headers(copyResponseHeaders(response.getHeaders()))
                .body(response.getBody());
    }

    // 根据 serviceKey 解析真实微服务名
    private String resolveServiceName(String serviceKey) {
        String serviceName = docsServiceProperties.getServices().get(serviceKey);
        if (serviceName == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown docs service: " + serviceKey);
        }
        return serviceName;
    }

    // 根据 serviceKey 解析 OpenAPI 文档路径；未单独配置时使用默认路径
    private String resolveApiDocsPath(String serviceKey) {
        String apiDocsPath = docsServiceProperties.getApiDocsPaths().get(serviceKey);
        return StringUtils.hasText(apiDocsPath) ? apiDocsPath : docsServiceProperties.getDefaultApiDocsPath();
    }

    // 根据 serviceKey 解析 AsyncAPI 文档路径；未单独配置时使用默认 Springwolf 路径
    private String resolveAsyncapiDocsPath(String serviceKey) {
        String asyncapiDocsPath = docsServiceProperties.getAsyncapiDocsPaths().get(serviceKey);
        return StringUtils.hasText(asyncapiDocsPath)
                ? asyncapiDocsPath
                : docsServiceProperties.getDefaultAsyncapiDocsPath();
    }

    // 通过服务发现构造目标微服务 URI
    private URI buildServiceUri(String serviceName, String path, String queryString) {
        ServiceInstance instance = loadBalancerClient.choose(serviceName);
        if (instance == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No instance available: " + serviceName);
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(instance.getUri())
                .path(normalizePath(path));
        if (StringUtils.hasText(queryString)) {
            builder.query(queryString);
        }
        return builder.build(true).toUri();
    }

    // 构造访问目标微服务时需要的内部 Header
    private HttpHeaders buildInternalHeaders(HttpHeaders headers) {
        // 直连微服务需要补 FromSource
        headers.set(SecurityConstants.HEADER_FROM_SOURCE, fromSource);

        // 透传灰度开发者标识
        String developerTag = GrayContextHolder.getDeveloperTag();
        if (StringUtils.hasText(developerTag)) {
            headers.set(CommonConstants.GRAY_HEADER_DEV_KEY, developerTag);
        }
        return headers;
    }

    // 复制浏览器请求 Header，并补充代理模式下需要的内部 Header
    private HttpHeaders copyRequestHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
            if (shouldSkipHeader(headerName)) {
                return;
            }
            request.getHeaders(headerName).asIterator()
                    .forEachRemaining(headerValue -> headers.add(headerName, headerValue));
        });
        return buildInternalHeaders(headers);
    }

    // 复制下游服务响应 Header
    private HttpHeaders copyResponseHeaders(HttpHeaders source) {
        HttpHeaders headers = new HttpHeaders();
        source.forEach((headerName, values) -> {
            if (!shouldSkipHeader(headerName)) {
                headers.addAll(headerName, values);
            }
        });
        return headers;
    }

    // 判断某个 Header 是否应该在代理过程中跳过
    private boolean shouldSkipHeader(String headerName) {
        return headerName == null || HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase(Locale.ROOT));
    }

    // 重写目标微服务返回的 OpenAPI JSON
    // 重写 servers，让 Scalar 的请求地址指向 docs-service 的代理接口，而不是直接请求目标微服务
    private String rewriteOpenApi(String body, String serviceKey, HttpServletRequest request) {
        try {
            JsonNode rootNode = objectMapper.readTree(body);
            if (!(rootNode instanceof ObjectNode root)) {
                return body;
            }

            ArrayNode servers = objectMapper.createArrayNode();
            ObjectNode server = objectMapper.createObjectNode();
            server.put("url", buildScalarProxyServerUrl(serviceKey, request));
            server.put("description", "WisePen Docs Proxy");
            servers.add(server);
            root.set("servers", servers);

            return objectMapper.writeValueAsString(root);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to rewrite OpenAPI document", e);
        }
    }

    // 构造写入 OpenAPI servers 的代理地址
    private String buildScalarProxyServerUrl(String serviceKey, HttpServletRequest request) {
        return normalizeContextPath(request.getContextPath())
                + trimTrailingSlash(normalizePath(docsServiceProperties.getProxyBasePath()))
                + "/"
                + serviceKey;
    }

    // 从当前代理请求 URI 中提取真正要转发到目标服务的 path
    private String extractProxyPath(String serviceKey, HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String prefix = normalizeContextPath(request.getContextPath())
                + trimTrailingSlash(normalizePath(docsServiceProperties.getProxyBasePath()))
                + "/"
                + serviceKey;

        if (!requestUri.startsWith(prefix)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid docs proxy path");
        }

        String path = requestUri.substring(prefix.length());
        return StringUtils.hasText(path) ? path : "/";
    }

    // 规范化 contextPath
    private String normalizeContextPath(String contextPath) {
        return StringUtils.hasText(contextPath) ? trimTrailingSlash(normalizePath(contextPath)) : "";
    }

    // 规范化 path，保证返回结果以 / 开头
    private String normalizePath(String path) {
        if (!StringUtils.hasText(path) || "/".equals(path)) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    // 去末尾斜杠
    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value) || "/".equals(value)) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
