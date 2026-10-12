# MontpelliENT 🎓🏛️

> **L'application mobile tout-en-un des étudiants de l'Université de Montpellier (UM).**  
> Équivalent d'un *Pronote universitaire*, éthique, ultra-rapide, respectueux de la vie privée et taillé sur-mesure pour le campus montpelliérain.

---

## 🌟 Fonctionnalités Principales

### 1. 📅 Emploi du Temps ADE Campus Intelligent
- **Connexion ADE UM :** Synchronisation directe avec ADE Campus (`proseconsult.umontpellier.fr`).
- **Mode Déconnecté :** Stockage local Room avec persistance hors-ligne complète.
- **Détection des Changements :** Mise en évidence immédiate des cours annulés, déplacés ou modifiés.
- **Affichage Complet :** Enseignant, salle, type d'enseignement (CM/TD/TP/Examen) et temps restant dynamique.

### 2. 📝 Moodle UM (Cours, Devoirs & Projets)
- **Synchronisation Moodle Mobile Web Services :** Récupération automatique des cours inscrits.
- **Suivi des Échéances :** Devoirs à rendre avec décompte, statut de remise (*Déposé*, *En retard*, etc.).
- **Filtres Avancés :** Filtrage par semestre, matière et statut de complétion.

### 3. 📊 Notes & Résultats Apogée / MonDossierWeb
- **Relevé Officiel :** Consultation des notes par semestre et unité d'enseignement (UE).
- **Simulateur de Moyenne Pondérée :** Calcul précis de la moyenne avec coefficients réels.
- **Règles de Compensation LMD :** Indicateurs visuels immédiats de validation du semestre et compensation.

### 4. 📬 Messagerie Étudiante UM (`@etu.umontpellier.fr`)
- **Connecteur IMAPS / SMTP natif :** Connexion directe et chiffrée SSL/TLS (993 / 587).
- **Gestion des Dossiers :** Boîte de réception, messages non lus et indicateurs visuels.
- **Composition de Courriel :** Rédaction et envoi direct depuis l'application avec validation des adresses `@umontpellier.fr`.

### 5. 🍽️ Vie de Campus & CROUS Montpellier-Occitanie
- **Restaurants Universitaires (RU) & Cafétérias :** Triolet, Richter, Boutonnet, Vert-Bois, Pharmacie, etc.
- **Menus du Jour :** Entrées, plats chauds, desserts et options végétariennes.
- **Affluence Collaborative en Temps Réel :** Partage anonyme du niveau d'affluence (Calme, Modéré, Chargé, Saturé) synchronisé via le serveur backend.
- **Services du Campus :** Horaires des Bibliothèques Universitaires (BU), SCAPS Sport, etc.

### 6. 📱 Widgets Android Glance (Écran d'accueil)
- **Prochain Cours :** Heure, salle, matière et temps restant mis à jour périodiquement.
- **Menu du CROUS :** Restaurant favori et plat du jour affichés directement sur l'écran d'accueil sans ouvrir l'application.

### 7. 🎨 Personnalisation Poussée & Ergonomie
- **Thèmes :** Mode Sombre, Mode Clair, Mode AMOLED (vrai noir économe en batterie).
- **Dynamic Colors :** Support complet de Material You (Android 12+).
- **Palette Hexadécimale Personnalisée :** Sélecteur libre de couleur primaire (Bleu UM `#003B71`, Rouge Université `#D32F2F`, Émeraude, etc.).
- **Recherche Globale Fédérée :** Barre de recherche universelle avec normalisation d'accents pour trouver instantanément cours, devoirs, restaurants, mails ou notes.

---

## 🔒 Sécurité & Vie Privée (Privacy by Design)

MontpelliENT applique une architecture stricte de **confidentialité absolue** :
- **Stockage Chiffré Local :** Identifiants et jetons CAS/ENT stockés dans le **Keystore Android** avec chiffrement AES-256-GCM via Google Tink / EncryptedSharedPreferences.
- **Aucun intermédiaire pour les données privées :** Les requêtes ADE, Moodle, Apogée et Zimbra s'effectuent **directement** entre le smartphone de l'étudiant et les serveurs de l'Université de Montpellier.
- **Backend Ktor 3 Éthique & Stateless :**
  - Le serveur Ktor ne stocke **aucun identifiant, ni mot de passe, ni note, ni mail personnel**.
  - Il fournit uniquement les flux publics (menus CROUS parsés, annuaires publics) et agrège l'affluence collaborative sous forme de compteurs anonymes expirant après 45 minutes.
- **Zero Google Play Services Dependency :** Compatible AOSP, GrapheneOS, CalyxOS, microG et F-Droid.

---

## 🏗️ Architecture du Projet

Le projet est un monorepo Gradle modulaire en Kotlin :

```
app_etudiante_ent/
├── android/                          # Application Mobile Android (Kotlin + Jetpack Compose)
│   ├── app/                          # Module Application (Navigation, DI Container, Widgets Glance)
│   ├── core/
│   │   ├── model/                    # Modèles métier partagés (Cours, Devoir, Mail, Note, RU...)
│   │   ├── designsystem/             # Composants UI Compose, Thèmes dynamiques, Couleurs UM
│   │   ├── security/                 # Keystore Android AES-256-GCM, gestionnaire de session sécurisé
│   │   ├── database/                 # Room Database, DAOs, Entités locales
│   │   └── data/                     # Repositories & Synchronisation offline-first
│   ├── connector/
│   │   ├── cas/                      # Connecteur CAS UM (cas.umontpellier.fr)
│   │   ├── ade/                      # Connecteur ADE Campus iCal / Web
│   │   ├── moodle/                   # Connecteur Moodle Web Services
│   │   ├── grades/                   # Connecteur Apogée / MonDossierWeb
│   │   └── mail/                     # Connecteur IMAPS / SMTP (@etu.umontpellier.fr)
│   └── feature/
│       ├── home/                     # Tableau de bord principal & recherche globale
│       ├── timetable/                # Vue Emploi du Temps interactive
│       ├── courses/                  # Vue Cours & Devoirs Moodle
│       ├── mail/                     # Vue Messagerie & Rédaction
│       ├── grades/                   # Vue Notes & Simulateur de moyenne
│       ├── crous/                    # Vue CROUS, Menus & Affluence
│       ├── campus/                   # Vue Vie de Campus & Services
│       ├── onboarding/               # Écrans de connexion & mode démo
│       └── settings/                 # Paramètres, Thèmes & Sélecteur de couleurs
│
├── server/                           # Backend Ktor 3 (Kotlin JVM)
│   ├── src/main/kotlin/fr/montpellient/server/
│   │   ├── Application.kt            # Point d'entrée Ktor Engine (Netty)
│   │   ├── routes/                   # Routes REST (/api/v1/crous, /api/v1/crowd, /api/v1/health)
│   │   ├── service/                  # Scraping menus CROUS & agrégateur affluence
│   │   └── plugins/                  # ContentNegotiation JSON, CORS, CallLogging
│   └── src/test/kotlin/              # Tests d'intégration Ktor
│
└── .github/workflows/ci.yml          # Pipeline CI/CD GitHub Actions
```

---

## 🚀 Démarrage & Installation

### Prérequis
- **JDK 21** (Temurin recommandé)
- **Android SDK** (API 34+ / Build-Tools 34.0.0)
- **Android Studio Ladybug+** ou ligne de commande Gradle

### 1. Démarrer le Serveur Ktor (Backend)
```bash
# Lancement en local (port 8080)
./gradlew :server:run
```
L'API expose :
- `GET http://localhost:8080/api/v1/health`
- `GET http://localhost:8080/api/v1/crous/restaurants`
- `POST http://localhost:8080/api/v1/crowd/report`

### 2. Compiler et Lancer l'Application Android
```bash
# Exécuter les tests unitaires
./gradlew testDebugUnitTest

# Installer et lancer sur émulateur ou appareil connecté
./gradlew :app:installDebug

# Ou compiler l'APK Release optimisé (R8 / ProGuard minifié ~3.1 Mo)
./gradlew :app:assembleRelease
```
L'APK produit se trouve dans :  
`android/app/build/outputs/apk/release/app-release-unsigned.apk`

---

## 🧪 Tests & Qualité
- **Tests Unitaires Android :** Connecteurs CAS, ADE iCal, Moodle, Apogée, Sécurité Keystore, Simulateur de notes.
- **Tests d'Intégration Serveur :** Routes Ktor, validation des payloads JSON, reporting affluence.
- **Minification R8 & ProGuard :** Vérification stricte des dépendances et de la taille de l'APK (seulement 3.1 Mo).

---

## 📜 Licence & Avertissement
Projet étudiant open-source sous licence MIT.  
*MontpelliENT est un client alternatif indépendant non affilié formellement à la DSI de l'Université de Montpellier ou au Crous de Montpellier-Occitanie. Les marques citées demeurent la propriété respective de leurs titulaires.*
