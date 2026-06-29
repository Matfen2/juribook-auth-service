package juribook.auth_service.service;

import juribook.auth_service.dto.request.RefreshTokenRequest;
import juribook.auth_service.dto.response.RefreshTokenResponse;
import juribook.auth_service.entity.RefreshToken;
import juribook.auth_service.entity.User;
import juribook.auth_service.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final long REFRESH_TOKEN_EXPIRY_DAYS = 7;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    // ── Créer un refresh token pour un utilisateur ───────────
    // Appelé lors du login
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        // Révoquer tous les tokens existants (un seul actif à la fois)
        refreshTokenRepository.revokeAllByUser(user);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(REFRESH_TOKEN_EXPIRY_DAYS));
        refreshToken.setRevoked(false);

        return refreshTokenRepository.save(refreshToken);
    }

    // ── Renouveler le JWT à partir d'un refresh token ────────
    @Transactional
    public RefreshTokenResponse refresh(RefreshTokenRequest request) {

        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(request.getRefreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Refresh token invalide"));

        if (refreshToken.isRevoked()) {
            throw new IllegalArgumentException("Refresh token révoqué");
        }

        if (refreshToken.isExpired()) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw new IllegalArgumentException("Refresh token expiré, veuillez vous reconnecter");
        }

        User user = refreshToken.getUser();

        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Ce compte est désactivé");
        }

        // ── Rotation : révoquer l'ancien, générer un nouveau ──
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        RefreshToken newRefreshToken = createRefreshToken(user);
        String newAccessToken = jwtService.generateToken(user);

        log.info("Token renouvelé : userId={}, email={}", user.getId(), user.getEmail());

        return RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken.getToken())
                .type("Bearer")
                .build();
    }

    // ── Révoquer tous les tokens (logout) ────────────────────
    @Transactional
    public void revokeAllTokens(User user) {
        refreshTokenRepository.revokeAllByUser(user);
        log.info("Tous les refresh tokens révoqués : userId={}", user.getId());
    }
}