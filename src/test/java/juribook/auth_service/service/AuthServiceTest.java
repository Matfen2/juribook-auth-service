package juribook.auth_service.service;

import juribook.auth_service.dto.request.LoginRequest;
import juribook.auth_service.dto.request.RegisterClientRequest;
import juribook.auth_service.dto.request.RegisterLawyerRequest;
import juribook.auth_service.dto.response.LoginResponse;
import juribook.auth_service.dto.response.RegisterClientResponse;
import juribook.auth_service.entity.LawyerStatus;
import juribook.auth_service.entity.RefreshToken;
import juribook.auth_service.entity.Role;
import juribook.auth_service.entity.User;
import juribook.auth_service.exception.UserNotFoundException;
import juribook.auth_service.repository.UserRepository;
import juribook.auth_service.security.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour AuthService.
 *
 * Stratégie : on utilise Mockito pour simuler (mocker) toutes les dépendances
 * (UserRepository, PasswordEncoder, JwtService, RefreshTokenService).
 * Cela permet de tester la logique métier de AuthService en isolation,
 * sans base de données ni réseau.
 *
 * Pattern AAA utilisé dans chaque test :
 *   - ARRANGE : préparer les données et configurer les mocks
 *   - ACT     : appeler la méthode à tester
 *   - ASSERT  : vérifier le résultat et les interactions
 */
@ExtendWith(MockitoExtension.class)   // Active Mockito pour injecter les mocks automatiquement
@DisplayName("AuthService — Tests unitaires")
class AuthServiceTest {

    // ── Mocks - dépendances simulées ─────────────────────────
    // @Mock crée un "faux" objet qui ne fait rien par défaut.
    // On configure son comportement via when(...).thenReturn(...)
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;

    // @InjectMocks crée une vraie instance de AuthService
    // et y injecte automatiquement les mocks ci-dessus
    @InjectMocks private AuthService authService;

    // ── Helpers - builders réutilisables ─────────────────────

    /**
     * Construit un utilisateur de test avec les propriétés minimales.
     * Évite la duplication de code dans chaque test.
     */
    private User buildUser(Long id, String email, Role role) {
        User user = new User();
        user.setId(id);
        user.setName("Test User");
        user.setEmail(email);
        user.setPassword("$2a$10$hashedpassword");  // mot de passe déjà hashé (BCrypt)
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }

    /**
     * Construit un refresh token valide (non révoqué, expire dans 7 jours).
     */
    private RefreshToken buildRefreshToken(User user) {
        RefreshToken rt = new RefreshToken();
        rt.setToken("test-uuid-refresh-token");
        rt.setUser(user);
        rt.setExpiresAt(LocalDateTime.now().plusDays(7));
        rt.setRevoked(false);
        return rt;
    }

    // ═══════════════════════════════════════════════════════════
    //  INSCRIPTION CLIENT — registerClient()
    //
    //  Cas couverts :
    //    ✅ Inscription réussie
    //    ✅ Email normalisé en minuscules
    //    ✅ Rôle CLIENT assigné automatiquement
    //    ❌ Email déjà utilisé
    // ═══════════════════════════════════════════════════════════
    @Nested
    @DisplayName("registerClient()")
    class RegisterClientTests {

        // Requête valide réutilisée dans plusieurs tests
        private RegisterClientRequest validRequest;

        @BeforeEach
        void setUp() {
            validRequest = new RegisterClientRequest();
            validRequest.setName("Jean Dupont");
            validRequest.setEmail("jean.dupont@gmail.com");
            validRequest.setPassword("motdepasse123");
            validRequest.setPhone("0612345678");
        }

        @Test
        @DisplayName("✅ Inscription réussie - retourne 201 avec les infos du client")
        void registerClient_success() {
            // ARRANGE
            // Simuler que l'email n'existe pas encore en BDD
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            // Simuler le hashage BCrypt (le vrai hashage prendrait ~100ms)
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
            // Simuler la sauvegarde en BDD → retourne l'utilisateur avec son ID
            User savedUser = buildUser(1L, "jean.dupont@gmail.com", Role.CLIENT);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // ACT
            RegisterClientResponse response = authService.registerClient(validRequest);

            // ASSERT : vérifier la réponse
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getEmail()).isEqualTo("jean.dupont@gmail.com");
            assertThat(response.getRole()).isEqualTo(Role.CLIENT);
            assertThat(response.getMessage()).isEqualTo("Inscription réussie");

            // ASSERT : vérifier les interactions avec les mocks
            // Le mot de passe doit être hashé avant d'être sauvegardé
            verify(passwordEncoder).encode("motdepasse123");
            // La sauvegarde doit être appelée exactement une fois
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("❌ Email déjà utilisé - lève IllegalArgumentException")
        void registerClient_emailAlreadyExists_throwsException() {
            // ARRANGE : simuler un email déjà présent en BDD
            when(userRepository.existsByEmail("jean.dupont@gmail.com")).thenReturn(true);

            // ACT + ASSERT : vérifier que l'exception est levée avec le bon message
            assertThatThrownBy(() -> authService.registerClient(validRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Un compte existe déjà avec cet email");

            // On ne doit JAMAIS sauvegarder si l'email est déjà utilisé
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("✅ L'email est normalisé en minuscules avant sauvegarde")
        void registerClient_emailNormalized() {
            // ARRANGE : email avec des majuscules
            validRequest.setEmail("Jean.DUPONT@Gmail.COM");
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
            User savedUser = buildUser(1L, "jean.dupont@gmail.com", Role.CLIENT);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // ACT
            authService.registerClient(validRequest);

            // ASSERT : vérifier que l'entité sauvegardée a l'email en minuscules
            // argThat() permet d'inspecter l'argument passé à save()
            verify(userRepository).save(argThat(user ->
                    user.getEmail().equals("jean.dupont@gmail.com")
            ));
        }

        @Test
        @DisplayName("✅ Le rôle CLIENT est assigné automatiquement (jamais LAWYER ni ADMIN)")
        void registerClient_roleIsClient() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
            when(userRepository.save(any(User.class)))
                    .thenReturn(buildUser(1L, "test@test.com", Role.CLIENT));

            authService.registerClient(validRequest);

            // Vérifier que le rôle sauvegardé est bien CLIENT
            verify(userRepository).save(argThat(user -> user.getRole() == Role.CLIENT));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  INSCRIPTION AVOCAT : registerLawyer()
    //
    //  Cas couverts :
    //    ✅ Inscription réussie → statut PENDING
    //    ❌ Email déjà utilisé
    //    ❌ Numéro de barreau déjà enregistré
    // ═══════════════════════════════════════════════════════════
    @Nested
    @DisplayName("registerLawyer()")
    class RegisterLawyerTests {

        private RegisterLawyerRequest validRequest;

        @BeforeEach
        void setUp() {
            validRequest = new RegisterLawyerRequest();
            validRequest.setName("Maître Sophie Martin");
            validRequest.setEmail("sophie.martin@avocat.fr");
            validRequest.setPassword("motdepasse123");
            validRequest.setBarNumber("75001");
            validRequest.setSpecialty("Droit du travail");
            validRequest.setCity("Paris");
        }

        @Test
        @DisplayName("✅ Inscription avocat réussie - statut PENDING obligatoire")
        void registerLawyer_success_statusIsPending() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByBarNumber(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
            // thenAnswer(inv -> inv.getArgument(0)) retourne l'argument tel quel
            // utile quand on veut inspecter l'entité sauvegardée
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            authService.registerLawyer(validRequest);

            // Vérifier que le statut est PENDING (pas APPROVED, l'admin valide manuellement)
            // et que le rôle est bien LAWYER
            verify(userRepository).save(argThat(user ->
                    user.getLawyerStatus() == LawyerStatus.PENDING &&
                    user.getRole() == Role.LAWYER
            ));
        }

        @Test
        @DisplayName("❌ Email déjà utilisé - lève IllegalArgumentException")
        void registerLawyer_emailAlreadyExists_throwsException() {
            when(userRepository.existsByEmail("sophie.martin@avocat.fr")).thenReturn(true);

            assertThatThrownBy(() -> authService.registerLawyer(validRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Email déjà utilisé");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("❌ Numéro de barreau déjà enregistré - lève IllegalArgumentException")
        void registerLawyer_barNumberAlreadyExists_throwsException() {
            // L'email est libre mais le numéro de barreau est déjà pris
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByBarNumber("75001")).thenReturn(true);

            assertThatThrownBy(() -> authService.registerLawyer(validRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Ce numéro de barreau est déjà enregistré");

            verify(userRepository, never()).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  LOGIN : login()
    //
    //  Cas couverts :
    //    ✅ Login réussi → JWT + refresh token
    //    ❌ Email introuvable
    //    ❌ Mot de passe incorrect
    //    ❌ Compte désactivé
    //    ✅ Message identique email/mdp (sécurité anti-énumération)
    // ═══════════════════════════════════════════════════════════
    @Nested
    @DisplayName("login()")
    class LoginTests {

        private LoginRequest validRequest;
        private User existingUser;

        @BeforeEach
        void setUp() {
            validRequest = new LoginRequest();
            validRequest.setEmail("jean.dupont@gmail.com");
            validRequest.setPassword("motdepasse123");

            existingUser = buildUser(1L, "jean.dupont@gmail.com", Role.CLIENT);
        }

        @Test
        @DisplayName("✅ Login réussi - retourne JWT + refresh token")
        void login_success_returnsTokens() {
            // ARRANGE
            when(userRepository.findByEmail("jean.dupont@gmail.com"))
                    .thenReturn(Optional.of(existingUser));
            // Simuler que le mot de passe fourni correspond au hash stocké
            when(passwordEncoder.matches("motdepasse123", existingUser.getPassword()))
                    .thenReturn(true);
            // Simuler la génération du JWT
            when(jwtService.generateToken(existingUser)).thenReturn("jwt-access-token");
            // Simuler la génération du refresh token
            when(refreshTokenService.createRefreshToken(existingUser))
                    .thenReturn(buildRefreshToken(existingUser));

            // ACT
            LoginResponse response = authService.login(validRequest);

            // ASSERT
            assertThat(response.getToken()).isEqualTo("jwt-access-token");
            assertThat(response.getRefreshToken()).isEqualTo("test-uuid-refresh-token");
            assertThat(response.getType()).isEqualTo("Bearer");
            assertThat(response.getRole()).isEqualTo(Role.CLIENT);
        }

        @Test
        @DisplayName("❌ Email introuvable - lève UserNotFoundException")
        void login_emailNotFound_throwsException() {
            // Simuler qu'aucun utilisateur n'existe avec cet email
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(validRequest))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("Email ou mot de passe incorrect");
        }

        @Test
        @DisplayName("❌ Mot de passe incorrect - lève UserNotFoundException")
        void login_wrongPassword_throwsException() {
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(existingUser));
            // Le mot de passe fourni ne correspond pas au hash
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> authService.login(validRequest))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("Email ou mot de passe incorrect");
        }

        @Test
        @DisplayName("❌ Compte désactivé - lève IllegalArgumentException")
        void login_accountDisabled_throwsException() {
            existingUser.setEnabled(false);  // désactiver le compte
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

            assertThatThrownBy(() -> authService.login(validRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Ce compte est désactivé");
        }

        @Test
        @DisplayName("✅ Sécurité - message identique pour email invalide et mot de passe incorrect")
        void login_sameErrorMessageForEmailAndPassword() {
            // Sécurité importante : si le message était différent selon le cas,
            // un attaquant pourrait deviner quels emails sont enregistrés
            // (technique dite "user enumeration").
            // On vérifie ici que le message est IDENTIQUE dans les deux cas.

            // Cas 1 : email inexistant
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
            Throwable emailError = catchThrowable(() -> authService.login(validRequest));

            // Cas 2 : mot de passe incorrect
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
            Throwable passwordError = catchThrowable(() -> authService.login(validRequest));

            // Les deux messages doivent être strictement identiques
            assertThat(emailError).hasMessage(passwordError.getMessage());
        }
    }
}