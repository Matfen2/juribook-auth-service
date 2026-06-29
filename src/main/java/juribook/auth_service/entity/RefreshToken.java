package juribook.auth_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
 
@Entity
@Table(name = "refresh_tokens")
@Data
public class RefreshToken {
 
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
 
    // Token opaque stocké en BDD (UUID)
    @Column(nullable = false, unique = true)
    private String token;
 
    // Utilisateur propriétaire du refresh token
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
 
    // Date d'expiration du refresh token (7 jours)
    @Column(nullable = false)
    private LocalDateTime expiresAt;
 
    // Permet la révocation sans supprimer l'entrée (audit)
    @Column(nullable = false)
    private boolean revoked = false;
 
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
 
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
 
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}