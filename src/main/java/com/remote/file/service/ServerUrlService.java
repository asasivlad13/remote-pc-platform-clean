package com.remote.file.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ServerUrlService {

    @Value("${remote.server.public-url:}")
    private String configuredPublicUrl;

    public String getBaseUrl(HttpServletRequest request) {
        if (configuredPublicUrl != null && !configuredPublicUrl.isBlank()) {
            return configuredPublicUrl.trim();
        }

        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();

        boolean defaultPort =
                ("http".equalsIgnoreCase(scheme) && port == 80) ||
                        ("https".equalsIgnoreCase(scheme) && port == 443);

        if (defaultPort) {
            return scheme + "://" + host;
        }

        return scheme + "://" + host + ":" + port;
    }
}