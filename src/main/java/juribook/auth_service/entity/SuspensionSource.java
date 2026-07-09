package juribook.auth_service.entity;

/**
 * Origine d'une suspension de compte : distingue une
 * suspension automatique (détection d'abus) d'une action admin
 * explicite, les trois passant par le même UserSuspensionService.
 * suspendAccount, seul le texte de `reason` différait auparavant (pas
 * fiable pour filtrer une liste dédiée aux abus détectés).
 */
public enum SuspensionSource {
    MANUAL,           // AdminUserService.deactivateUser
    ABUSE_DETECTION,  // AbuseEventConsumer, suite à abuse.detected
    LAWYER_REJECTION  // AdminService.updateLawyerStatus, branche REJECTED
}