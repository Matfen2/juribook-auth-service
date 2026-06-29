package juribook.auth_service.service;

import juribook.auth_service.dto.request.RefreshTokenRequest;
import juribook.auth_service.dto.response.RefreshTokenResponse;
import juribook.auth_service.entity.RefreshToken;
import juribook.auth_service.entity.Role;
import juribook.auth_service.entity.User;
import juribook.auth_service.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour RefreshTokenService.
 *
 * Le refresh token est la clé de la session longue durée (7 jours).
 * Ces tests vérifient les règles de sécurité critiques :
 *   - Un token révoqué ne peut jamais être réutilisé
 *   - Un token expiré est révoqué puis rejeté
 *   - La rotation est appliquée à chaque utilisation (nouveau token généré)
 *   - Un compte désactivé ne peut pas rafraîchir son token
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService — Tests unitaires")
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtService jwtService;

    @InjectMocks private RefreshTokenService refreshTokenService;

    // Utilisateur de test partagé entre les sous-classes
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("jean@test.com");
        testUser.setRole(Role.CLIENT);
        testUser.setEnabled(true);
    }

    /**
     * Construit un RefreshToken avec les propriétés configurables.
     *
     * @param revoked   true si le token doit être révoqué
     * @param expiresAt date d'expiration (passée = expiré, future = valide)
     */
    private RefreshToken buildToken(boolean revoked, LocalDateTime expiresAt) {
        RefreshToken rt = new RefreshToken();
        rt.setToken("test-refresh-token-uuid");
        rt.setUser(testUser);
        rt.setRevoked(revoked);
        rt.setExpiresAt(expiresAt);
        return rt;
    }

    // ═══════════════════════════════════════════════════════════
    //  REFRESH : refresh()
    //
    //  Scénarios testés :
    //    ✅ Refresh réussi → nouveaux access + refresh tokens
    //    ✅ Rotation → l'ancien token est révoqué
    //    ❌ Token inexistant (UUID inconnu)
    //    ❌ Token révoqué (déjà utilisé ou logout)
    //    ❌ Token expiré (plus de 7 jours)
    //    ❌ Compte désactivé
    // ═══════════════════════════════════════════════════════════
    @Nested
    @DisplayName("refresh()")
    class RefreshTests {

        // Requête de refresh réutilisée dans les tests
        private RefreshTokenRequest request;

        @BeforeEach
        void setUp() {
            request = new RefreshTokenRequest();
            request.setRefreshToken("test-refresh-token-uuid");
        }

        @Test
        @DisplayName("✅ Refresh réussi — retourne un nouveau access token et un nouveau refresh token")
        void refresh_success_returnsNewTokens() {
            // ARRANGE : token valide (non révoqué, expire dans 7 jours)
            RefreshToken validToken = buildToken(false, LocalDateTime.now().plusDays(7));
            when(refreshTokenRepository.findByToken("test-refresh-token-uuid"))
                    .thenReturn(Optional.of(validToken));
            when(jwtService.generateToken(testUser)).thenReturn("new-access-token");

            // Le nouveau refresh token a un UUID différent
            RefreshToken newRefreshToken = buildToken(false, LocalDateTime.now().plusDays(7));
            newRefreshToken.setToken("new-refresh-token-uuid");
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(newRefreshToken);

            // ACT
            RefreshTokenResponse response = refreshTokenService.refresh(request);

            // ASSERT : les deux tokens sont bien retournés
            assertThat(response.getAccessToken()).isEqualTo("new-access-token");
            assertThat(response.getType()).isEqualTo("Bearer");
            assertThat(response.getRefreshToken()).isNotNull();
        }

        @Test
        @DisplayName("✅ Rotation — l'ancien refresh token est révoqué après utilisation")
        void refresh_success_oldTokenIsRevoked() {
            // ARRANGE
            RefreshToken validToken = buildToken(false, LocalDateTime.now().plusDays(7));
            when(refreshTokenRepository.findByToken(anyString()))
                    .thenReturn(Optional.of(validToken));
            when(jwtService.generateToken(any())).thenReturn("new-access-token");
            when(refreshTokenRepository.save(any())).thenReturn(validToken);

            // ACT
            refreshTokenService.refresh(request);

            // ASSERT : le token original doit être marqué révoqué (rotation)
            // Si ce n'était pas le cas, le même refresh token pourrait être utilisé
            // indéfiniment ou par un attaquant qui l'aurait volé.
            assertThat(validToken.isRevoked()).isTrue();
        }

        @Test
        @DisplayName("❌ Token inexistant - lève IllegalArgumentException")
        void refresh_tokenNotFound_throwsException() {
            // Simuler un UUID qui n'existe pas en BDD (token forgé ou expiré + nettoyé)
            when(refreshTokenRepository.findByToken(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService.refresh(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Refresh token invalide");
        }

        @Test
        @DisplayName("❌ Token révoqué - lève IllegalArgumentException")
        void refresh_revokedToken_throwsException() {
            // Un token révoqué peut indiquer :
            //   - Que l'utilisateur s'est déconnecté
            //   - Que le token a déjà été utilisé (rotation) → possible vol détecté
            RefreshToken revokedToken = buildToken(true, LocalDateTime.now().plusDays(7));
            when(refreshTokenRepository.findByToken(anyString()))
                    .thenReturn(Optional.of(revokedToken));

            assertThatThrownBy(() -> refreshTokenService.refresh(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Refresh token révoqué");
        }

        @Test
        @DisplayName("❌ Token expiré - est révoqué proprement puis lève IllegalArgumentException")
        void refresh_expiredToken_isRevokedThenThrows() {
            // Token dont la date d'expiration est dans le passé (hier)
            RefreshToken expiredToken = buildToken(false, LocalDateTime.now().minusDays(1));
            when(refreshTokenRepository.findByToken(anyString()))
                    .thenReturn(Optional.of(expiredToken));
            when(refreshTokenRepository.save(any())).thenReturn(expiredToken);

            assertThatThrownBy(() -> refreshTokenService.refresh(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Refresh token expiré, veuillez vous reconnecter");

            // Important : le token expiré doit être révoqué en BDD
            // pour éviter qu'il soit réutilisé si quelqu'un essaie à nouveau
            assertThat(expiredToken.isRevoked()).isTrue();
        }

        @Test
        @DisplayName("❌ Compte désactivé - lève IllegalArgumentException même avec token valide")
        void refresh_disabledUser_throwsException() {
            // Scénario : l'admin désactive un compte pendant une session active.
            // Le refresh token est toujours valide mais le compte ne l'est plus.
            testUser.setEnabled(false);
            RefreshToken validToken = buildToken(false, LocalDateTime.now().plusDays(7));
            when(refreshTokenRepository.findByToken(anyString()))
                    .thenReturn(Optional.of(validToken));

            assertThatThrownBy(() -> refreshTokenService.refresh(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Ce compte est désactivé");
        }
    }
}