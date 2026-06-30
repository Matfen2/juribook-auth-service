package juribook.auth_service.repository;

import juribook.auth_service.entity.LawyerStatus;
import juribook.auth_service.entity.Role;
import juribook.auth_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Auth
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByBarNumber(String barNumber);

    // Admin : listing par rôle et statut
    List<User> findByRole(Role role);
    List<User> findByRoleAndLawyerStatus(Role role, LawyerStatus lawyerStatus);

    // Admin : comptages pour les stats
    long countByRole(Role role);
    long countByRoleAndLawyerStatus(Role role, LawyerStatus lawyerStatus);
}