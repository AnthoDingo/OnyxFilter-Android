# OnyxFilter pour Android

Application Android pour piloter une instance [OnyxFilter](https://github.com/AnthoDingo/OnyxFilter) :
elle permet d'**activer ou de désactiver le filtrage DNS**, sans limite de durée ou temporairement,
depuis l'application, des widgets de l'écran d'accueil ou une tuile des réglages rapides.

## Fonctionnalités

- Connexion à l'instance avec son adresse et un **jeton d'API** ; la session est conservée entre deux
  lancements.
- État de la protection en temps réel : actualisation toutes les 15 secondes quand l'écran est
  visible, compte à rebours et heure de réactivation pendant une désactivation temporaire.
- Désactivation **sans limite de durée** (bouton principal, comme sur le tableau de bord web).
- Désactivation **temporaire** : 30 secondes, 1 minute, 10 minutes, 1 heure, jusqu'à demain
  (minuit, heure du téléphone), ou durée personnalisée jusqu'à 30 jours. La durée d'une désactivation
  en cours peut être modifiée.
- Réactivation immédiate.

La protection est gérée par le serveur : elle est réactivée automatiquement à l'échéance, même si le
téléphone est éteint, et toujours réactivée au redémarrage d'OnyxFilter.

### Widgets de l'écran d'accueil

- **Bascule OnyxFilter** (1 × 1) : bouclier vert ou rouge et état en un coup d'œil (heure de
  réactivation pendant une désactivation temporaire) ; un toucher désactive la protection sans
  limite de durée, ou la réactive.
- **Protection OnyxFilter** (4 × 2, redimensionnable) : état détaillé, boutons *10 min*, *1 h* et
  *Demain* pour une désactivation temporaire, et bouton *Désactiver* / *Activer*. Toucher l'en-tête
  ouvre l'application.

### Tuile des réglages rapides

Une tuile **OnyxFilter** s'ajoute au volet des réglages rapides (à côté de la lampe torche, du Wi-Fi…) :
allumée quand le filtrage est actif, elle le désactive sans limite de durée ou le réactive d'un
toucher. Sous son nom (Android 10+) : *Activée*, *Désactivée*, *Jusqu'à 14:32* pendant une
désactivation temporaire, ou *Hors ligne*. Un appui long ouvre l'application, pour choisir une durée.
Depuis l'écran de verrouillage, le téléphone doit d'abord être déverrouillé.

Les widgets et la tuile se mettent à jour après chacune de leurs actions, à chaque changement fait dans
l'application, juste après la fin d'une désactivation temporaire, et toutes les 30 minutes (pour
refléter les changements faits depuis l'interface web) ; la tuile relit aussi l'état à l'ouverture du
volet (au plus une fois toutes les 30 secondes). Serveur injoignable : ils gardent le dernier
état connu et l'indiquent (« Hors ligne ») ; un toucher relance la lecture. Sans session, ils ouvrent
l'écran de connexion.

## Côté serveur : l'API HTTP d'OnyxFilter

L'application utilise l'API HTTP d'OnyxFilter (`/api/v1`), sans modification du serveur. Elle
s'authentifie par un **jeton d'API** : dans l'interface web, ouvrir *Paramètres › Accès API*, créer un
jeton (par exemple « Téléphone ») et le copier — il n'est affiché qu'une fois. Le révoquer depuis la même
page déconnecte l'application.

Points d'accès utilisés (en-tête `Authorization: Bearer <jeton>`) :

| Méthode | Chemin | Usage |
|---|---|---|
| `GET` | `/api/v1/protection` | état ; sert aussi à vérifier le jeton à la connexion |
| `POST` | `/api/v1/protection/disable` | `{}` : sans limite ; `{ "durationSeconds": 600 }` : pendant 10 min |
| `POST` | `/api/v1/protection/enable` | réactivation |
| `GET` | `/api/v1/stats` | statistiques des dernières 24 heures |

La durée d'une désactivation est transmise en secondes, et l'application affiche la durée restante
renvoyée par le serveur : l'affichage ne dépend pas de la synchronisation des horloges du téléphone et du
serveur. Une instance sans `/api/v1` (version antérieure) est signalée à la connexion.

## Utilisation

Saisir l'adresse de l'interface web d'OnyxFilter, par exemple `https://onyxfilter.maison:7037` ou
`http://192.168.1.10:5259` (sans schéma, `https://` est ajouté), et le jeton d'API. Un chemin est accepté
pour une instance servie derrière un proxy inverse (`https://maison.example/onyxfilter`).

- **HTTP** est autorisé pour les instances uniquement joignables sur le réseau local ; l'écran de
  connexion signale alors que le jeton transite en clair.
- **Certificat auto-signé** : installer le certificat de l'autorité qui l'a émis dans
  *Paramètres → Sécurité → Chiffrement et identifiants → Installer un certificat → Certificat CA* ;
  l'application fait confiance aux autorités installées par l'utilisateur.

## Compilation

Android Studio (récent), ou en ligne de commande avec un JDK 17 ou plus récent (jusqu'au JDK 25) et le
SDK Android (API 37). Le wrapper télécharge Gradle 9.8 et en vérifie l'empreinte.

```sh
./gradlew assembleDebug          # APK : app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # tests unitaires (JVM)
./gradlew lintDebug
```

Outils : Android Gradle Plugin 9.4 (Kotlin intégré), Kotlin 2.4, Jetpack Compose (BOM 2026.09),
Material 3. L'intégration continue (GitHub Actions, `.github/workflows/android.yml`) exécute les tests et
le lint et publie l'APK de débogage en artefact de chaque exécution.

Version minimale : Android 8.0 (API 26) ; cible : Android 17 (API 37).

## Sécurité

- Le jeton d'API est chiffré (AES-GCM) avec une clé de l'Android Keystore qui ne quitte pas l'appareil ;
  il est exclu des sauvegardes et des transferts entre appareils. Aucun mot de passe n'est demandé ni
  conservé.
- Un jeton donne accès à toute l'API : en créer un par appareil, pour pouvoir le révoquer seul.

## Structure

```
app/src/main/java/io/github/anthodingo/onyxfilter/
├── data/     client HTTP (OkHttp), modèles JSON, session chiffrée, dépôt
├── domain/   durées de désactivation, état de la protection, statistiques, calculs d'affichage
├── ui/       écrans Compose (connexion, protection), ViewModels, thème
├── tile/     tuile des réglages rapides
└── widget/   widgets de l'écran d'accueil (RemoteViews) et état partagé avec la tuile
```
