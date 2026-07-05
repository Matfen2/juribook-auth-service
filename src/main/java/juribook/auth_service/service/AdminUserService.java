package juribook.auth_service.service;

import juribook.auth_service.dto.response.AdminUserResponse;
import juribook.auth_service.entity.Role;
import juribook.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recherche admin des utilisateurs. Aucun appel
 * inter-services : role/enabled/city sont tous des champs locaux de
 * User (city est dénormalisé côté avocat, cf. User.java).
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> searchUsers(Role role, Boolean enabled, String city, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        return userRepository.search(role, enabled, city, pageable)
                .map(AdminUserResponse::from);
    }
}