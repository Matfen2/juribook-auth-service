package juribook.auth_service.entity;

public enum LawyerStatus {
    PENDING,    // En attente de validation par l'admin
    APPROVED,   // Profil validé - l'avocat peut recevoir des réservations
    REJECTED    // Profil refusé par l'admin
}