package juribook.auth_service.filter;

import juribook.auth_service.entity.User;
import juribook.auth_service.repository.UserRepository;
import juribook.auth_service.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtre JWT exécuté UNE SEULE FOIS par requête HTTP (OncePerRequestFilter).
 *
 * Rôle : intercepter chaque requête entrante, extraire et valider le token JWT
 * présent dans le header "Authorization", puis injecter l'utilisateur authentifié
 * dans le SecurityContext de Spring Security.
 *
 * Flux d'une requête authentifiée :
 *   1. Requête HTTP → JwtAuthenticationFilter
 *   2. Extraction du token depuis "Authorization: Bearer <token>"
 *   3. Décodage du token → extraction de l'email (subject)
 *   4. Chargement de l'utilisateur depuis la BDD
 *   5. Validation du token (signature + expiration + compte actif)
 *   6. Injection de l'authentification dans le SecurityContext
 *   7. Passage au filtre suivant dans la chaîne (filterChain.doFilter)
 *   8. Spring Security autorise ou refuse la requête selon les règles de SecurityConfig
 *
 * Note : OncePerRequestFilter garantit que request et filterChain
 * ne sont jamais null — les annotations @NonNull ont été retirées
 * pour éviter les faux positifs d'analyse statique.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // ── Étape 1 : Lire le header Authorization ───────────────────────────
        // Format attendu : "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
        // Si le header est absent ou ne commence pas par "Bearer ", la requête
        // continue sans authentification (Spring Security appliquera ses règles
        // et retournera 401 si la route est protégée).
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("Pas de token JWT dans la requête : {} {}",
                    request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // ── Étape 2 : Extraire le token (supprimer le préfixe "Bearer ") ─────
        // "Bearer eyJhbG..." → "eyJhbG..."
        final String token = authHeader.substring(7);

        try {
            // ── Étape 3 : Décoder le token et extraire l'email (subject) ─────
            // Le subject est défini lors de la génération du token dans JwtService.
            // Si le token est malformé ou la signature invalide, une exception est levée.
            final String email = jwtService.extractEmail(token);

            // ── Étape 4 : Vérifier qu'aucune auth n'est déjà en contexte ─────
            // Si le SecurityContext contient déjà une authentification (ex: requête
            // déjà traitée par un autre filtre), on ne réauthentifie pas.
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // ── Étape 5 : Charger l'utilisateur depuis la BDD ────────────
                // On vérifie que l'utilisateur existe toujours en BDD (il peut avoir
                // été supprimé ou désactivé depuis la génération du token).
                User user = userRepository.findByEmail(email).orElse(null);

                // ── Étape 6 : Valider le token ───────────────────────────────
                // Triple vérification :
                //   - user != null       → l'utilisateur existe en BDD
                //   - isTokenValid()     → signature valide + non expiré + email cohérent
                //   - user.isEnabled()   → compte actif (pas banni/désactivé)
                if (user != null && jwtService.isTokenValid(token, email) && user.isEnabled()) {

                    // ── Étape 7 : Construire l'objet d'authentification ───────
                    // Spring Security utilise des "GrantedAuthority" pour les rôles.
                    // Convention Spring : les rôles doivent être préfixés par "ROLE_"
                    // → hasRole("CLIENT") dans SecurityConfig correspond à "ROLE_CLIENT" ici.
                    List<SimpleGrantedAuthority> authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
                    );

                    // UsernamePasswordAuthenticationToken = objet d'auth standard Spring Security
                    // Paramètres : principal (user), credentials (null = pas besoin du mdp),
                    //              authorities (liste des rôles)
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(user, null, authorities);

                    // Attacher les détails de la requête HTTP (IP, session...) à l'auth
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // ── Étape 8 : Injecter dans le SecurityContext ────────────
                    // À partir de ce moment, Spring Security considère la requête
                    // comme authentifiée avec le rôle de cet utilisateur.
                    // Les règles de SecurityConfig (.hasRole(), @PreAuthorize) s'appliqueront.
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    log.debug("JWT valide — utilisateur authentifié : email={}, role={}",
                            email, user.getRole());

                } else {
                    // Token valide syntaxiquement mais utilisateur introuvable,
                    // désactivé, ou token expiré → Spring Security retournera 401
                    log.warn("Token rejeté : utilisateur introuvable, désactivé ou token expiré — email={}",
                            email);
                }
            }

        } catch (Exception e) {
            // Token malformé, signature invalide, ou toute autre erreur JWT.
            // On logue sans lever d'exception : Spring Security retournera 401
            // automatiquement sur les routes protégées.
            log.warn("Erreur lors du traitement du JWT : {}", e.getMessage());
        }

        // ── Étape 9 : Passer au filtre suivant dans la chaîne ────────────────
        // Que l'authentification ait réussi ou non, on continue la chaîne.
        // Spring Security appliquera ses règles d'accès après ce filtre.
        filterChain.doFilter(request, response);
    }
}