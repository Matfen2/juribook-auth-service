package juribook.auth_service.security;

import juribook.auth_service.entity.Role;
import juribook.auth_service.entity.User;
import juribook.auth_service.service.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitaires pour JwtService.
 *
 * JwtService ne dépend d'aucun bean Spring, on l'instancie directement.
 * ReflectionTestUtils permet d'injecter les @Value (secret, expiration)
 * sans démarrer le contexte Spring, ce qui rend les tests très rapides.
 *
 * Cas couverts :
 *   ✅ Génération d'un token non vide
 *   ✅ Extraction correcte de l'email (subject)
 *   ✅ Token valide pour le bon utilisateur
 *   ❌ Token invalide pour un autre email
 *   ❌ Token expiré
 *   ❌ Token malformé
 *   ✅ Claims contiennent le rôle et l'id
 */
@DisplayName("JwtService - Tests unitaires")
class JwtServiceTest {

    // Instance réelle (pas un mock) - on teste la vraie logique JWT
    private JwtService jwtService;

    // Secret identique à celui de application.yml
    // Doit faire au moins 256 bits (32 caractères) pour HS256
    private static final String SECRET =
            "juribook-secret-key-must-be-at-least-256-bits-long-for-hs256";

    // 24h en millisecondes
    private static final long EXPIRATION = 86400000L;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // ReflectionTestUtils injecte les valeurs des @Value sans contexte Spring
        // Équivalent à : @Value("${jwt.secret}") private String secret;
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
        ReflectionTestUtils.setField(jwtService, "expiration", EXPIRATION);
    }

    /**
     * Construit un utilisateur de test minimal.
     * On n'a pas besoin de mot de passe ni d'autres champs pour tester le JWT.
     */
    private User buildUser(Long id, String email, Role role) {
        User user = new User();
        user.setId(id);
        user.setName("Test User");
        user.setEmail(email);
        user.setRole(role);
        return user;
    }

    @Test
    @DisplayName("✅ generateToken() - retourne un token JWT non vide")
    void generateToken_returnsNonEmptyToken() {
        User user = buildUser(1L, "jean@test.com", Role.CLIENT);

        String token = jwtService.generateToken(user);

        // Un JWT valide a la forme : header.payload.signature (3 parties séparées par des points)
        assertThat(token)
                .isNotBlank()
                .contains(".");  // au moins un point → structure JWT
    }

    @Test
    @DisplayName("✅ extractEmail() - retourne l'email encodé dans le subject du token")
    void extractEmail_returnsCorrectEmail() {
        User user = buildUser(1L, "jean@test.com", Role.CLIENT);
        String token = jwtService.generateToken(user);

        // Le subject du JWT doit contenir l'email de l'utilisateur
        String extractedEmail = jwtService.extractEmail(token);

        assertThat(extractedEmail).isEqualTo("jean@test.com");
    }

    @Test
    @DisplayName("✅ isTokenValid() - token valide pour le bon utilisateur")
    void isTokenValid_validToken_returnsTrue() {
        User user = buildUser(1L, "jean@test.com", Role.CLIENT);
        String token = jwtService.generateToken(user);

        // Le token doit être valide si l'email correspond et qu'il n'est pas expiré
        assertThat(jwtService.isTokenValid(token, "jean@test.com")).isTrue();
    }

    @Test
    @DisplayName("❌ isTokenValid() - token invalide pour un email différent")
    void isTokenValid_wrongEmail_returnsFalse() {
        // Le token est généré pour jean@test.com
        User user = buildUser(1L, "jean@test.com", Role.CLIENT);
        String token = jwtService.generateToken(user);

        // Mais on vérifie avec un autre email → doit retourner false
        // Cas réel : token volé et utilisé avec un autre compte
        assertThat(jwtService.isTokenValid(token, "autre@test.com")).isFalse();
    }

    @Test
    @DisplayName("❌ isTokenValid() - token expiré retourne false")
    void isTokenValid_expiredToken_returnsFalse() {
        // Définir une expiration négative → le token sera déjà expiré à la génération
        ReflectionTestUtils.setField(jwtService, "expiration", -1L);

        User user = buildUser(1L, "jean@test.com", Role.CLIENT);
        String expiredToken = jwtService.generateToken(user);

        // Le token expiré ne doit jamais être accepté
        assertThat(jwtService.isTokenValid(expiredToken, "jean@test.com")).isFalse();
    }

    @Test
    @DisplayName("❌ isTokenValid() - token malformé retourne false (pas d'exception)")
    void isTokenValid_malformedToken_returnsFalse() {
        // Un token malformé ou tronqué ne doit pas lever d'exception
        // mais retourner false silencieusement (géré dans le catch du JwtAuthenticationFilter)
        assertThat(jwtService.isTokenValid("token.invalide.ici", "jean@test.com")).isFalse();
        assertThat(jwtService.isTokenValid("", "jean@test.com")).isFalse();
        assertThat(jwtService.isTokenValid("pas-du-tout-un-jwt", "jean@test.com")).isFalse();
    }

    @Test
    @DisplayName("✅ extractClaims() - les claims contiennent le rôle et l'id utilisateur")
    void extractClaims_containsRoleAndId() {
        // Ces claims sont utilisés côté frontend pour afficher le dashboard selon le rôle
        // et éviter de refaire un appel API pour connaître le rôle de l'utilisateur
        User user = buildUser(42L, "jean@test.com", Role.LAWYER);
        String token = jwtService.generateToken(user);

        var claims = jwtService.extractClaims(token);

        // Vérifier que le rôle est bien encodé dans le token
        assertThat(claims.get("role", String.class)).isEqualTo("LAWYER");
        // Vérifier que l'id est bien encodé (utile pour identifier l'utilisateur sans requête BDD)
        assertThat(claims.get("id", Long.class)).isEqualTo(42L);
    }
}