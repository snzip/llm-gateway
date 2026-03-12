package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.persistence.entity.OAuthAccessTokenEntity;
import com.qizlan.llm.gateway.persistence.entity.OAuthAuthorizationCodeEntity;
import com.qizlan.llm.gateway.persistence.entity.OAuthClientEntity;
import com.qizlan.llm.gateway.persistence.repository.OAuthAccessTokenRepository;
import com.qizlan.llm.gateway.persistence.repository.OAuthAuthorizationCodeRepository;
import com.qizlan.llm.gateway.persistence.repository.OAuthClientRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OAuthService {

    private final OAuthClientRepository clientRepository;
    private final OAuthAuthorizationCodeRepository authorizationCodeRepository;
    private final OAuthAccessTokenRepository accessTokenRepository;

    public OAuthService(
            OAuthClientRepository clientRepository,
            OAuthAuthorizationCodeRepository authorizationCodeRepository,
            OAuthAccessTokenRepository accessTokenRepository
    ) {
        this.clientRepository = clientRepository;
        this.authorizationCodeRepository = authorizationCodeRepository;
        this.accessTokenRepository = accessTokenRepository;
    }

    public OAuthClientEntity registerClient(String clientName, List<String> grantTypes, String redirectUri) {
        return clientRepository.save(new OAuthClientEntity(
                clientName == null || clientName.isBlank() ? "llmgateway-client" : clientName,
                String.join(" ", grantTypes == null || grantTypes.isEmpty() ? List.of("authorization_code", "client_credentials") : grantTypes),
                redirectUri
        ));
    }

    public OAuthAuthorizationCodeEntity issueAuthorizationCode(String clientId, String redirectUri, String state) {
        OAuthClientEntity client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown OAuth client: " + clientId));
        if (!client.isActive()) {
            throw new IllegalArgumentException("OAuth client is inactive: " + clientId);
        }
        return authorizationCodeRepository.save(new OAuthAuthorizationCodeEntity(clientId, redirectUri, state));
    }

    public OAuthAccessTokenEntity exchangeToken(Map<String, Object> request) {
        String grantType = stringValue(request, "grant_type", "client_credentials");
        return switch (grantType) {
            case "authorization_code" -> exchangeAuthorizationCode(request);
            case "client_credentials" -> issueClientCredentialsToken(request);
            default -> throw new IllegalArgumentException("Unsupported grant_type: " + grantType);
        };
    }

    private OAuthAccessTokenEntity exchangeAuthorizationCode(Map<String, Object> request) {
        String code = stringValue(request, "code", "");
        OAuthAuthorizationCodeEntity authCode = authorizationCodeRepository.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown authorization code"));
        if (authCode.isConsumed()) {
            throw new IllegalArgumentException("Authorization code already consumed");
        }
        authCode.setConsumed(true);
        authorizationCodeRepository.save(authCode);
        return accessTokenRepository.save(new OAuthAccessTokenEntity(authCode.getClientId(), stringValue(request, "scope", "mcp"), 3600));
    }

    private OAuthAccessTokenEntity issueClientCredentialsToken(Map<String, Object> request) {
        String clientId = stringValue(request, "client_id", "");
        OAuthClientEntity client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown OAuth client: " + clientId));
        String providedSecret = stringValue(request, "client_secret", "");
        if (!client.getClientSecret().equals(providedSecret)) {
            throw new IllegalArgumentException("Invalid client secret");
        }
        return accessTokenRepository.save(new OAuthAccessTokenEntity(client.getClientId(), stringValue(request, "scope", "mcp"), 3600));
    }

    private String stringValue(Map<String, Object> request, String key, String fallback) {
        if (request == null) {
            return fallback;
        }
        Object value = request.get(key);
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }
}
