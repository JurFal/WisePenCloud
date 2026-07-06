package com.oriole.wisepen.docs.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "wisepen.docs")
public class DocsServiceProperties {

    private Map<String, String> services = new LinkedHashMap<>();
    private Map<String, String> apiDocsPaths = new LinkedHashMap<>();
    private Map<String, String> asyncapiDocsPaths = new LinkedHashMap<>();
    private String defaultApiDocsPath = "/v3/api-docs";
    private String defaultAsyncapiDocsPath = "/springwolf/docs";
    private String proxyBasePath = "/docs/proxy";

}
