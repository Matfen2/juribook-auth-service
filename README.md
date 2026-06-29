# juribook-auth-service

Microservice d'authentification pour **JuriBook**, gestion des inscriptions, connexions, refresh tokens et protection des routes par rôle via JWT.

## Stack

- Java 21 · Spring Boot 4.1.0 · Maven
- Spring Security · JWT (JJWT 0.12.6) · BCrypt
- PostgreSQL 16 · Flyway
- Apache Kafka (producer - topic `audit-events`)
- Springdoc OpenAPI (Swagger UI)
- JUnit 5 + Mockito (35 tests unitaires)
- Port : **8081**

## Structure du projet

```
src/main/java/juribook/auth_service/
├── config/
│   ├── SecurityConfig.java           # Règles d'accès par rôle + filtre JWT + bean PasswordEncoder
│   ├── CorsConfig.java               # CORS pour le frontend (localhost:5173)
│   └── OpenApiConfig.java            # Configuration Swagger UI
├── controller/
│   ├── AuthController.java           # POST /register, /register/lawyer, /login, /refresh, /logout
│   └── UserController.java           # GET /me, /lawyer-profile, /client-dashboard
├── dto/
│   ├── request/
│   │   ├── LoginRequest.java
│   │   ├── RegisterClientRequest.java
│   │   ├── RegisterLawyerRequest.java
│   │   └── RefreshTokenRequest.java
│   └── response/
│       ├── LoginResponse.java
│       ├── RegisterClientResponse.java
│       ├── RegisterLawyerResponse.java
│       └── RefreshTokenResponse.java
├── entity/
│   ├── User.java                     # Entité JPA principale
│   ├── Role.java                     # Enum : CLIENT, LAWYER, ADMIN
│   ├── LawyerStatus.java             # Enum : PENDING, APPROVED, REJECTED
│   └── RefreshToken.java             # Token de renouvellement de session (7 jours)
├── exception/
│   ├── GlobalExceptionHandler.java   # Handlers 400/404/409/500
│   └── UserNotFoundException.java
├── filter/
│   └── JwtAuthenticationFilter.java  # Filtre Spring Security (OncePerRequestFilter)
├── repository/
│   ├── UserRepository.java
│   └── RefreshTokenRepository.java
├── security/
│   └── JwtService.java               # Génération et validation des tokens JWT
└── service/
    ├── AuthService.java              # Logique métier inscription + login
    └── RefreshTokenService.java      # Rotation des refresh tokens
src/main/resources/
├── application.yml
└── db/migration/
    ├── V1__create_users_table.sql
    └── V2__create_refresh_tokens_table.sql
src/test/java/juribook/auth_service/
├── AuthServiceApplicationTests.java  # Smoke test (sans @SpringBootTest)
├── controller/
│   └── AuthControllerTest.java       # 13 tests - endpoints register, login, logout
├── security/
│   └── JwtServiceTest.java           # 7 tests - génération, validation, claims
└── service/
    ├── AuthServiceTest.java          # 12 tests - inscription, login, anti-énumération
    └── RefreshTokenServiceTest.java  # 6 tests - rotation, révocation, expiration
src/test/resources/
└── application-test.yml              # H2 en mémoire + Flyway désactivé pour CI
```

## Lancer les tests

```bash
mvn test
```

```
Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Lancer en local (hors Docker)

```bash
# Prérequis : PostgreSQL sur localhost:5432 avec la base authdb
mvn spring-boot:run
```

## Lancer via Docker Compose

```bash
# Depuis juribook-docker/docker/
docker compose up -d postgres-auth auth-service
```

## Swagger UI

[http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)

## Health check

[http://localhost:8081/actuator/health](http://localhost:8081/actuator/health)

---

## Endpoints

### Publics (pas de token requis)

| Méthode | URL | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Inscription client |
| `POST` | `/api/auth/register/lawyer` | Inscription avocat (statut PENDING) |
| `POST` | `/api/auth/login` | Connexion → JWT (24h) + refresh token (7j) |
| `POST` | `/api/auth/refresh` | Renouveler le JWT sans redemander le mot de passe |
| `POST` | `/api/auth/logout` | Révoquer tous les refresh tokens de l'utilisateur |

### Protégés (token JWT requis)

| Méthode | URL | Rôle requis | Description |
|---|---|---|---|
| `GET` | `/api/users/me` | Tous | Profil de l'utilisateur connecté |
| `GET` | `/api/users/lawyer-profile` | `LAWYER` | Profil avocat |
| `GET` | `/api/users/client-dashboard` | `CLIENT` | Dashboard client |

---

## Exemples Postman

### Inscription client
```json
POST http://localhost:8081/api/auth/register
Content-Type: application/json

{
    "name": "Jean Dupont",
    "email": "jean.dupont@gmail.com",
    "password": "motdepasse123",
    "phone": "0612345678"
}
```

### Inscription avocat
```json
POST http://localhost:8081/api/auth/register/lawyer
Content-Type: application/json

{
    "name": "Maître Sophie Martin",
    "email": "sophie.martin@avocat.fr",
    "password": "motdepasse123",
    "phone": "0698765432",
    "barNumber": "75001",
    "specialty": "Droit du travail",
    "city": "Paris"
}
```

### Login
```json
POST http://localhost:8081/api/auth/login
Content-Type: application/json

{
    "email": "jean.dupont@gmail.com",
    "password": "motdepasse123"
}
```

Réponse :
```json
{
    "token": "eyJhbGci...",
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
    "type": "Bearer",
    "id": 1,
    "name": "Jean Dupont",
    "email": "jean.dupont@gmail.com",
    "role": "CLIENT"
}
```

### Renouveler le token (refresh)
```json
POST http://localhost:8081/api/auth/refresh
Content-Type: application/json

{
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

Réponse :
```json
{
    "accessToken": "eyJhbGci...",
    "refreshToken": "nouveau-uuid-généré",
    "type": "Bearer"
}
```

> ⚠️ Le refresh token est **à usage unique** (rotation). Chaque appel révoque l'ancien et en génère un nouveau.

### Utiliser le token dans Postman
> **Authorization** → **Bearer Token** → coller le token retourné par `/login`

Ou dans le header :
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### GET /api/users/me - profil de l'utilisateur connecté (tous rôles)
```
GET http://localhost:8081/api/users/me
Authorization: Bearer <token>
```
Réponse — 200 :
```json
{
    "id": 1,
    "name": "Jean Dupont",
    "email": "jean.dupont@gmail.com",
    "role": "CLIENT"
}
```

### GET /api/users/lawyer-profile - réservé aux avocats
```
GET http://localhost:8081/api/users/lawyer-profile
Authorization: Bearer <token_avocat>
```
Réponse — 200 (LAWYER) :
```json
{
    "id": 2,
    "name": "Maître Sophie Martin",
    "barNumber": "75001",
    "specialty": "Droit du travail",
    "city": "Paris",
    "lawyerStatus": "PENDING"
}
```
Réponse — 403 si CLIENT :
```json
{
    "message": "Accès interdit : permissions insuffisantes"
}
```

### GET /api/users/client-dashboard - réservé aux clients
```
GET http://localhost:8081/api/users/client-dashboard
Authorization: Bearer <token_client>
```
Réponse — 200 (CLIENT) :
```json
{
    "id": 1,
    "name": "Jean Dupont",
    "email": "jean.dupont@gmail.com",
    "message": "Bienvenue sur votre espace client"
}
```
Réponse — 403 si LAWYER :
```json
{
    "message": "Accès interdit : permissions insuffisantes"
}
```

---

## Codes HTTP retournés

| Code | Cas |
|---|---|
| 201 | Inscription réussie |
| 200 | Login ou consultation réussis |
| 204 | Logout réussi |
| 400 | Données invalides (champ manquant, format incorrect, règle métier) |
| 401 | Token absent ou invalide |
| 403 | Rôle insuffisant |
| 404 | Utilisateur introuvable (email ou mot de passe incorrect) |
| 409 | Email ou numéro de barreau déjà utilisé |
| 500 | Erreur inattendue |

---

## Commandes SQL utiles

### Accéder à la base PostgreSQL

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb
```

### Lister tous les utilisateurs

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "SELECT * FROM users;"
```

### Lister uniquement les clients

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "SELECT id, name, email, role, enabled, created_at FROM users WHERE role = 'CLIENT';"
```

### Lister uniquement les avocats

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "SELECT id, name, email, bar_number, specialty, city, lawyer_status FROM users WHERE role = 'LAWYER';"
```

### Lister les avocats en attente de validation

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "SELECT id, name, email, bar_number, specialty, city, lawyer_status FROM users WHERE role = 'LAWYER' AND lawyer_status = 'PENDING';"
```

### Vérifier les refresh tokens actifs

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "SELECT id, user_id, LEFT(token, 8) AS token_preview, expires_at, revoked FROM refresh_tokens WHERE revoked = false;"
```

### Vérifier qu'un mot de passe est bien hashé (BCrypt)

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "SELECT email, LEFT(password, 20) AS password_hash_preview FROM users;"
```

### Compter les utilisateurs par rôle

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "SELECT role, COUNT(*) FROM users GROUP BY role;"
```

### Supprimer un utilisateur de test

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "DELETE FROM users WHERE email = 'jean.dupont@gmail.com';"
```

### Vérifier les migrations Flyway

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "SELECT version, description, installed_on, success FROM flyway_schema_history ORDER BY installed_rank;"
```

---

## Variables d'environnement

| Variable | Description | Valeur par défaut |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL PostgreSQL | `jdbc:postgresql://localhost:5432/authdb` |
| `SPRING_DATASOURCE_USERNAME` | Utilisateur PostgreSQL | `juribook` |
| `SPRING_DATASOURCE_PASSWORD` | Mot de passe PostgreSQL | `juribook` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Adresse Kafka | `localhost:9092` |
| `JWT_SECRET` | Secret JWT (min. 256 bits) | valeur de dev |

---

## Rôles et règles d'accès

| Rôle | Description | Accès |
|---|---|---|
| `CLIENT` | Particulier cherchant un avocat | Recherche, réservation, avis |
| `LAWYER` | Avocat inscrit (validé par admin) | Gestion profil, disponibilités, rendez-vous |
| `ADMIN` | Administrateur plateforme | Tout + validation avocats + audit |

> ⚠️ Un avocat nouvellement inscrit a le statut `PENDING` — il ne peut pas accéder aux routes `LAWYER` tant que l'admin ne l'a pas validé (`APPROVED`).

---

## Refresh token — fonctionnement

```
Login           → JWT (24h) + refresh token (7 jours)
JWT expiré      → POST /api/auth/refresh → nouveau JWT + nouveau refresh token
Logout          → révocation de tous les refresh tokens de l'utilisateur
Token révoqué   → 400 Refresh token révoqué
Token expiré    → 400 Refresh token expiré, veuillez vous reconnecter
```

La **rotation** est obligatoire : chaque utilisation d'un refresh token révoque l'ancien et en génère un nouveau. Si un token déjà consommé est réutilisé, c'est un signal de vol détecté.