package juribook.auth_service.repository;

import juribook.auth_service.entity.RefreshToken;
import juribook.auth_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
 
import java.util.Optional;
 
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
 
    Optional<RefreshToken> findByToken(String token);
 
    // Révocation de tous les tokens d'un utilisateur (ex: déconnexion globale)
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user = :user AND r.revoked = false")
    void revokeAllByUser(User user);
 
    // Nettoyage des tokens expirés (à appeler via un job planifié)
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < CURRENT_TIMESTAMP OR r.revoked = true")
    void deleteExpiredAndRevoked();
}