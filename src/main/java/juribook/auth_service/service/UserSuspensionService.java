package juribook.auth_service.service;

import juribook.auth_service.entity.User;
import juribook.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Suspension de compte suite à une détection d'abus (Sprint 6.10).
 *
 * Réutilise le champ `enabled` déjà existant sur User (déjà vérifié
 * dans AuthService.login — "Ce compte est désactivé") plutôt que
 * d'introduire un nouveau booléen `suspended` redondant. Seuls
 * `suspendedReason`/`suspendedAt` sont de nouvelles colonnes, pour
 * garder une trace de POURQUOI le compte a été désactivé (utile pour
 * un futur écran admin de consultation) — `enabled=false` reste la
 * seule donnée réellement vérifiée au login, ces deux colonnes ne sont
 * que des métadonnées d'audit.
 *
 * ⚠️ N'invalide PAS un JWT déjà émis — cf. limite déjà documentée :
 * un token émis avant la désactivation reste valide jusqu'à son
 * expiration naturelle (24h), aucune liste de révocation partagée
 * entre les 6 services. Le blocage n'est effectif qu'à la PROCHAINE
 * tentative de connexion.
 *
 * ⚠️ suspendAccount reste générique par rôle (suspend n'importe quel
 * compte par son id), même si dans la pratique actuelle actorId dans
 * abuse.detected est toujours un clientId (les deux signaux d'abus du
 * Sprint 6.9 proviennent tous deux de clientId).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSuspensionService {

    private final UserRepository userRepository;

    @Transactional
    public void suspendAccount(Long userId, String reason) {
        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            log.warn("Suspension impossible — utilisateur introuvable : id={}", userId);
            return;
        }

        if (!user.isEnabled()) {
            log.debug("Compte déjà désactivé, aucune action supplémentaire : id={}", userId);
            return;
        }

        user.setEnabled(false);
        user.setSuspendedReason(reason);
        user.setSuspendedAt(LocalDateTime.now());
        userRepository.save(user);

        log.warn("Compte désactivé suite à détection d'abus : id={}, email={}, reason={}",
                user.getId(), user.getEmail(), reason);
    }
}