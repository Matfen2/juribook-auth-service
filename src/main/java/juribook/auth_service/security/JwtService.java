package juribook.auth_service.security;

import juribook.auth_service.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration; // en ms (86400000 = 24h)

    // ── Générer un token JWT ─────────────────────────────────
    public String generateToken(User user) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        JwtBuilder builder = Jwts.builder()
                .subject(user.getEmail())
                .claim("id", user.getId())
                .claim("role", user.getRole().name())
                .claim("name", user.getName())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration));

        // barNumber : null pour CLIENT/ADMIN, uniquement renseigné pour
        // LAWYER. Claim ajouté seulement si non-null, cohérent avec
        // JwtService.extractBarNumber() côté lawyer-service, qui traite
        // déjà son absence comme un cas normal (retourne null sans lever
        // d'exception), pas la peine d'ajouter une valeur null explicite.
        //
        // ⚠️ Correctif : ce claim manquait entièrement jusqu'ici, ce qui
        // faisait échouer POST /api/lawyers/profile côté lawyer-service
        // avec un 500 (barNumber null → violation de contrainte NOT NULL
        // en base, non catchée spécifiquement par le GlobalExceptionHandler).
        if (user.getBarNumber() != null) {
            builder.claim("barNumber", user.getBarNumber());
        }

        return builder.signWith(key).compact();
    }

    // ── Extraire les claims ──────────────────────────────────
    public Claims extractClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ── Extraire l'email (subject) ───────────────────────────
    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    // ── Vérifier si le token est expiré ─────────────────────
    public boolean isTokenValid(String token, String email) {
        try {
            Claims claims = extractClaims(token);
            return claims.getSubject().equals(email)
                    && claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}