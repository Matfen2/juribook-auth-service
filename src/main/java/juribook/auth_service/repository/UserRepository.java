package juribook.auth_service.repository;

import juribook.auth_service.entity.LawyerStatus;
import juribook.auth_service.entity.Role;
import juribook.auth_service.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByBarNumber(String barNumber);

    List<User> findByRole(Role role);
    List<User> findByRoleAndLawyerStatus(Role role, LawyerStatus lawyerStatus);

    long countByRole(Role role);
    long countByRoleAndLawyerStatus(Role role, LawyerStatus lawyerStatus);

    @Query("""
        SELECT u FROM User u
        WHERE (:role IS NULL OR u.role = :role)
          AND (:enabled IS NULL OR u.enabled = :enabled)
          AND (:city IS NULL OR LOWER(u.city) = LOWER(:city))
        ORDER BY u.createdAt DESC
    """)
    Page<User> search(@Param("role") Role role,
                       @Param("enabled") Boolean enabled,
                       @Param("city") String city,
                       Pageable pageable);
}