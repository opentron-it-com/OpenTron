package org.opentron.backend.services;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.opentron.backend.storage.entities.OAuthToken;
import org.opentron.backend.storage.repositories.OAuthTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

@Service
public class OAuthService {

    private static final Logger logger = LoggerFactory.getLogger(OAuthService.class);

    private final OAuthTokenRepository tokenRepo;

    private final WebClient webClient;

    public OAuthService(OAuthTokenRepository tokenRepo) {
        this.tokenRepo = tokenRepo;
        this.webClient = WebClient.create();
    }

    @SuppressWarnings("rawtypes")
    public OAuthToken exchangeCode(String connectorId, String provider, String tokenEndpoint, String clientId, String clientSecret, String code, String redirectUri) {
        try {
            WebClient.RequestBodySpec req = webClient.post().uri(tokenEndpoint).contentType(MediaType.APPLICATION_FORM_URLENCODED);
            BodyInserters.FormInserter<String> inserter = BodyInserters.fromFormData("grant_type", "authorization_code")
                    .with("code", code);
            if (redirectUri != null) inserter = inserter.with("redirect_uri", redirectUri);
            if (clientId != null) inserter = inserter.with("client_id", clientId);
            if (clientSecret != null) inserter = inserter.with("client_secret", clientSecret);

            Mono<Map> resp = req.body(inserter).retrieve().bodyToMono(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> tokenResp = resp.block();
            if (tokenResp == null) throw new RuntimeException("empty token response");
            return fromCodeExchange(connectorId, provider, tokenResp);
        } catch (Exception e) {
            logger.error("Error exchanging code for connector {}: {}", connectorId, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public OAuthToken saveToken(String connectorId, String provider, String accessToken, String refreshToken, Instant expiresAt) {
        OAuthToken t = new OAuthToken();
        t.setConnectorId(connectorId);
        t.setProvider(provider);
        t.setAccessToken(accessToken);
        t.setRefreshToken(refreshToken);
        t.setExpiresAt(expiresAt);
        OAuthToken saved = tokenRepo.save(t);
        logger.info("Saved OAuth token for connector {} id={}", connectorId, saved.getId());
        return saved;
    }

    public OAuthToken fromCodeExchange(String connectorId, String provider, Map<String, Object> tokenResponse) {
        String accessToken = tokenResponse.getOrDefault("access_token", "").toString();
        String refreshToken = tokenResponse.getOrDefault("refresh_token", "").toString();
        Instant expiresAt = Instant.now().plusSeconds(3600);
        if (tokenResponse.containsKey("expires_in")) {
            try {
                long ex = Long.parseLong(tokenResponse.get("expires_in").toString());
                expiresAt = Instant.now().plusSeconds(ex);
            } catch (Exception ignored) {}
        }
        return saveToken(connectorId, provider, accessToken, refreshToken, expiresAt);
    }
}
