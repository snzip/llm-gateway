package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.persistence.entity.OAuthAccessTokenEntity;
import com.qizlan.llm.gateway.persistence.entity.OAuthAuthorizationCodeEntity;
import com.qizlan.llm.gateway.persistence.entity.OAuthClientEntity;
import com.qizlan.llm.gateway.persistence.repository.OAuthAccessTokenRepository;
import com.qizlan.llm.gateway.persistence.repository.OAuthAuthorizationCodeRepository;
import com.qizlan.llm.gateway.persistence.repository.OAuthClientRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthService {

    private static final long ACCESS_TOKEN_TTL_SECONDS = 3600;

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
        assertGrantSupported(client, "authorization_code");
        if (client.getRedirectUri() != null && !client.getRedirectUri().isBlank()
                && redirectUri != null && !redirectUri.isBlank()
                && !client.getRedirectUri().equals(redirectUri)) {
            throw new IllegalArgumentException("Invalid redirect_uri for client: " + clientId);
        }
        return authorizationCodeRepository.save(new OAuthAuthorizationCodeEntity(clientId, redirectUri, state));
    }

    @Transactional
    public OAuthAccessTokenEntity exchangeToken(Map<String, Object> request) {
        String grantType = stringValue(request, "grant_type", "client_credentials");
        return switch (grantType) {
            case "authorization_code" -> exchangeAuthorizationCode(request);
            case "client_credentials" -> issueClientCredentialsToken(request);
            case "refresh_token" -> exchangeRefreshToken(request);
            default -> throw new IllegalArgumentException("Unsupported grant_type: " + grantType);
        };
    }

    @Transactional
    private OAuthAccessTokenEntity exchangeAuthorizationCode(Map<String, Object> request) {
        String code = stringValue(request, "code", "");
        OAuthAuthorizationCodeEntity authCode = authorizationCodeRepository.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown authorization code"));
        if (authCode.isConsumed()) {
            throw new IllegalArgumentException("Authorization code already consumed");
        }
        authCode.setConsumed(true);
        authorizationCodeRepository.save(authCode);
        OAuthClientEntity client = clientRepository.findById(authCode.getClientId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown OAuth client: " + authCode.getClientId()));
        assertActiveClient(client);
        assertGrantSupported(client, "authorization_code");
        return issueToken(client.getClientId(), stringValue(request, "scope", "mcp"), true);
    }

    @Transactional
    private OAuthAccessTokenEntity issueClientCredentialsToken(Map<String, Object> request) {
        OAuthClientEntity client = authenticateClient(request);
        assertGrantSupported(client, "client_credentials");
        return issueToken(client.getClientId(), stringValue(request, "scope", "mcp"), false);
    }

    @Transactional
    private OAuthAccessTokenEntity exchangeRefreshToken(Map<String, Object> request) {
        OAuthClientEntity client = authenticateClient(request);
        assertGrantSupported(client, "refresh_token");
        String refreshToken = stringValue(request, "refresh_token", "");
        OAuthAccessTokenEntity existing = accessTokenRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("Unknown refresh token"));
        if (!existing.getClientId().equals(client.getClientId())) {
            throw new IllegalArgumentException("Refresh token does not belong to client: " + client.getClientId());
        }
        if (existing.isRevoked()) {
            throw new IllegalArgumentException("Refresh token already revoked");
        }
        if (existing.isExpired()) {
            throw new IllegalArgumentException("Refresh token expired");
        }
        existing.revoke();
        accessTokenRepository.save(existing);
        return issueToken(client.getClientId(), stringValue(request, "scope", existing.getScope()), true);
    }

    @Transactional
    public void revokeToken(Map<String, Object> request) {
        String token = stringValue(request, "token", "");
        if (token.isBlank()) {
            throw new IllegalArgumentException("token is required");
        }
        OAuthAccessTokenEntity accessToken = accessTokenRepository.findById(token)
                .orElseGet(() -> accessTokenRepository.findByRefreshToken(token).orElseThrow(() ->
                        new IllegalArgumentException("Unknown token")));
        if (!accessToken.isRevoked()) {
            accessToken.revoke();
            accessTokenRepository.save(accessToken);
        }
    }

    @Transactional
    public OAuthClientEntity rotateClientSecret(String clientId) {
        OAuthClientEntity client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown OAuth client: " + clientId));
        assertActiveClient(client);
        client.setClientSecret("mcp-secret-" + UUID.randomUUID().toString().replace("-", ""));
        return clientRepository.save(client);
    }

    public OAuthAccessTokenEntity validateAccessToken(String accessToken, String requiredScope) {
        OAuthAccessTokenEntity token = accessTokenRepository.findById(accessToken)
                .orElseThrow(() -> new IllegalArgumentException("Unknown access token"));
        if (token.isRevoked()) {
            throw new IllegalArgumentException("Access token revoked");
        }
        if (token.isExpired()) {
            throw new IllegalArgumentException("Access token expired");
        }
        if (requiredScope != null && !requiredScope.isBlank()) {
            List<String> scopes = List.of(token.getScope().split(" "));
            if (!scopes.contains(requiredScope)) {
                throw new IllegalArgumentException("Access token missing scope: " + requiredScope);
            }
        }
        OAuthClientEntity client = clientRepository.findById(token.getClientId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown OAuth client: " + token.getClientId()));
        assertActiveClient(client);
        return token;
    }

    private OAuthAccessTokenEntity issueToken(String clientId, String scope, boolean issueRefreshToken) {
        OAuthAccessTokenEntity token = new OAuthAccessTokenEntity(clientId, scope, ACCESS_TOKEN_TTL_SECONDS);
        if (!issueRefreshToken) {
            token.setRefreshToken("");
        }
        return accessTokenRepository.save(token);
    }

    private OAuthClientEntity authenticateClient(Map<String, Object> request) {
        String clientId = stringValue(request, "client_id", "");
        OAuthClientEntity client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown OAuth client: " + clientId));
        assertActiveClient(client);
        String providedSecret = stringValue(request, "client_secret", "");
        if (!client.getClientSecret().equals(providedSecret)) {
            throw new IllegalArgumentException("Invalid client secret");
        }
        return client;
    }

    private void assertActiveClient(OAuthClientEntity client) {
        if (!client.isActive()) {
            throw new IllegalArgumentException("OAuth client is inactive: " + client.getClientId());
        }
    }

    private void assertGrantSupported(OAuthClientEntity client, String grantType) {
        List<String> supportedGrants = List.of(client.getGrantTypes().split(" "));
        if (!supportedGrants.contains(grantType)) {
            throw new IllegalArgumentException("OAuth client does not support grant_type: " + grantType);
        }
    }

    private String stringValue(Map<String, Object> request, String key, String fallback) {
        if (request == null) {
            return fallback;
        }
        Object value = request.get(key);
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }
}
