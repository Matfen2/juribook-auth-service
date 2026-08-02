package juribook.auth_service.repository;

import juribook.auth_service.entity.LawyerStatus;
import juribook.auth_service.entity.Role;
import juribook.auth_service.entity.SuspensionSource;
import juribook.auth_service.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ⚠️ Reconstruit à partir des usages observés dans AuthService/
 * AdminService/AdminUserService (jamais vu en entier), vérifie contre
 * ta version réelle avant d'appliquer, notamment si d'autres méthodes
 * existent déjà que je n'ai pas reproduites ici.
 *
 * search() étend le filtre cumulable existant avec
 * suspensionSource, pour que la page "Alertes d'abus" (frontend)
 * réutilise ce même endpoint générique plutôt qu'un endpoint dédié.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByBarNumber(String barNumber);

    List<User> findByRole(Role role);

    List<User> findByRoleAndLawyerStatus(Role role, LawyerStatus lawyerStatus);

    long countByRole(Role role);

    long countByRoleAndLawyerStatus(Role role, LawyerStatus lawyerStatus);

    /**
     * Consultation filtrée admin : tous les paramètres
     * sont optionnels et cumulables.
     *
     * suspensionSource ajouté au même pattern IS NULL OR.
     */
    @Query("""
        SELECT u FROM User u
        WHERE (:role IS NULL OR u.role = :role)
          AND (:enabled IS NULL OR u.enabled = :enabled)
          AND (:city IS NULL OR u.city = :city)
          AND (:suspensionSource IS NULL OR u.suspensionSource = :suspensionSource)
        ORDER BY u.createdAt DESC
    """)
    Page<User> search(
        @Param("role") Role role,
        @Param("enabled") Boolean enabled,
        @Param("city") String city,
        @Param("suspensionSource") SuspensionSource suspensionSource,
        Pageable pageable
    );
}