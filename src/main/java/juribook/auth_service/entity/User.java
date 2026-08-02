package juribook.auth_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
 
@Entity
@Table(name = "users")
@Data
public class User {
 
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
 
    @Column(nullable = false, length = 100)
    private String name;
 
    @Column(nullable = false, unique = true)
    private String email;
 
    @Column(length = 20)
    private String phone;
 
    @Column(nullable = false)
    private String password;
 
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;
 
    @Column(nullable = false)
    private boolean enabled = true;
 
    // ── Champs spécifiques aux avocats ──────────────────────
    // null pour les CLIENT et ADMIN
 
    @Column(unique = true, length = 20)
    private String barNumber;      // Numéro de barreau (ex: 75001)
 
    @Column(length = 100)
    private String specialty;      // Spécialité juridique (ex: Droit du travail)
 
    @Column(length = 100)
    private String city;           // Ville d'exercice (ex: Paris)
 
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private LawyerStatus lawyerStatus; // Statut de validation admin (null si CLIENT/ADMIN)
 
    // ── Audit ───────────────────────────────────────────────
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
 
    @Column(nullable = false)
    private LocalDateTime updatedAt;
 
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
 
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Column(name = "suspended_reason", length = 200)
    private String suspendedReason;
 
    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    // null si le compte n'a jamais été suspendu. Distingue une
    // suspension automatique (détection d'abus) d'une action admin
    // explicite (désactivation manuelle ou refus de profil avocat).
    @Enumerated(EnumType.STRING)
    @Column(name = "suspension_source", length = 20)
    private SuspensionSource suspensionSource;
}