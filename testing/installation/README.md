# AsciiFrame Installation Tests

Ce répertoire contient la suite de tests complète pour valider les installations d'AsciiFrame sur différents environnements et plateformes.

## 🎯 Objectifs

- **Validation des releases** : S'assurer que chaque release fonctionne correctement
- **Tests multi-plateformes** : Ubuntu, Alpine Linux, CentOS
- **Tests multi-modes** : Installation standalone, Docker, globale
- **Environnements isolés** : Tests dans des containers propres
- **Intégration CI/CD** : Validation automatique sur GitHub Actions

## 📁 Structure

```
testing/installation/
├── docker/                          # Environnements de test containerisés
│   ├── Dockerfile.ubuntu            # Test Ubuntu 22.04
│   ├── Dockerfile.alpine            # Test Alpine Linux 3.18
│   ├── Dockerfile.centos            # Test CentOS Stream 9
│   └── docker-compose.test.yml      # Orchestration des tests
├── scripts/                         # Scripts de test
│   ├── test-installation.sh         # Test principal d'installation
│   └── run-multiplatform-tests.sh   # Orchestrateur multi-plateforme
├── fixtures/                        # Données de test (vide par défaut)
└── README.md                        # Cette documentation
```

## 🚀 Utilisation

### Tests locaux simples

```bash
# Test d'installation local (mode simple)
cd testing/installation
./scripts/test-installation.sh

# Test spécifique d'un mode
./scripts/test-installation.sh --standalone-only
./scripts/test-installation.sh --docker-only
```

### Tests multi-plateformes

```bash
# Test sur toutes les plateformes
./scripts/run-multiplatform-tests.sh

# Test sur une plateforme spécifique
./scripts/run-multiplatform-tests.sh --ubuntu-only

# Tests séquentiels (plus lent mais plus stable)
./scripts/run-multiplatform-tests.sh --sequential

# Garder les containers pour debug
./scripts/run-multiplatform-tests.sh --keep-containers
```

### Tests avec Docker Compose

```bash
# Lancer tous les tests en parallèle
cd testing/installation/docker
docker-compose -f docker-compose.test.yml up

# Test d'une plateforme spécifique
docker-compose -f docker-compose.test.yml up test-ubuntu
```

## 🔧 Configuration

### Variables d'environnement

#### Pour `test-installation.sh`
- `INSTALL_MODES` : Modes à tester (défaut: "standalone docker global")
- `SKIP_CLEANUP` : Garder les fichiers de test (défaut: "false")

#### Pour `run-multiplatform-tests.sh`
- `TEST_PLATFORMS` : Plateformes à tester (défaut: "ubuntu alpine centos")
- `PARALLEL_TESTS` : Tests parallèles (défaut: "true")
- `KEEP_CONTAINERS` : Garder les containers (défaut: "false")

### Exemples

```bash
# Test seulement les modes standalone et Docker
INSTALL_MODES="standalone docker" ./scripts/test-installation.sh

# Test seulement Ubuntu et Alpine
TEST_PLATFORMS="ubuntu alpine" ./scripts/run-multiplatform-tests.sh

# Test séquentiel avec containers gardés pour debug
PARALLEL_TESTS="false" KEEP_CONTAINERS="true" ./scripts/run-multiplatform-tests.sh
```

## 📊 Résultats

### Structure des résultats

```
testing/results/YYYYMMDD_HHMMSS/
├── TEST_REPORT.md              # Rapport complet
├── test-ubuntu.log             # Log de test Ubuntu
├── test-alpine.log             # Log de test Alpine
├── test-centos.log             # Log de test CentOS
├── build-*.log                 # Logs de build des images
├── result-*.txt                # Résultats par plateforme
└── artifacts-*/                # Artefacts de test par plateforme
    └── testuser/               # Répertoire utilisateur du test
        ├── standalone/         # Test installation standalone
        ├── docker/             # Test installation Docker
        └── global/             # Test installation globale
```

### Interprétation des résultats

- **SUCCESS** : Installation réussie et fonctionnelle
- **FAILED** : Échec d'installation ou de fonctionnement
- **NO RESULT** : Échec de build de l'image de test

## 🔍 Types de tests effectués

### Tests d'installation

1. **Vérification des prérequis**
   - Curl/wget disponibles
   - Java (si mode standalone)
   - Docker (si mode Docker)

2. **Test standalone**
   - Téléchargement du JAR
   - Création du script wrapper
   - Vérification de l'exécution
   - Test de génération de base

3. **Test Docker**
   - Création du docker-compose.yml
   - Vérification de la configuration
   - Test des fichiers créés
   - Validation Docker Compose

4. **Test global** (si applicable)
   - Installation dans ~/.local/bin
   - Vérification des permissions
   - Test d'accès global

### Tests fonctionnels

- Génération de documents de test
- Vérification des formats de sortie
- Test des configurations
- Validation des thèmes

## 🐛 Débogage

### Logs détaillés

```bash
# Garder les artefacts pour analyse
SKIP_CLEANUP=true ./scripts/test-installation.sh

# Examiner les logs
ls /tmp/asciiframe-test-*/test.log
```

### Containers pour debug

```bash
# Garder les containers après les tests
KEEP_CONTAINERS=true ./scripts/run-multiplatform-tests.sh

# Se connecter au container
docker exec -it asciiframe-test-ubuntu-12345 /bin/bash
```

### Tests manuels

```bash
# Build et test manuel d'une image
cd testing/installation
docker build -f docker/Dockerfile.ubuntu -t test-ubuntu .
docker run -it --privileged -v /var/run/docker.sock:/var/run/docker.sock test-ubuntu /bin/bash
```

## 🔄 Intégration CI/CD

Les tests sont automatiquement exécutés via GitHub Actions :

- **Push sur main/develop** : Tests complets
- **Pull Requests** : Tests avec rapport dans les commentaires
- **Manuel** : Workflow dispatch avec options personnalisées

### Workflow `.github/workflows/installation-tests.yml`

- Tests matriciels sur toutes les plateformes
- Upload des artefacts de test
- Génération de rapports
- Commentaires automatiques sur les PRs

## 📋 Checklist de validation release

Avant une release, vérifier que :

- [ ] Tests d'installation passent sur Ubuntu
- [ ] Tests d'installation passent sur Alpine
- [ ] Tests d'installation passent sur CentOS
- [ ] Mode standalone fonctionne
- [ ] Mode Docker fonctionne
- [ ] Scripts d'installation sont à jour
- [ ] Documentation d'installation est correcte

## 🤝 Contribution

Pour ajouter de nouveaux tests :

1. **Nouvelle plateforme** : Ajouter `Dockerfile.nouvelle-plateforme`
2. **Nouveau mode** : Modifier `test-installation.sh`
3. **Nouveaux scénarios** : Ajouter dans `fixtures/`

Les tests doivent être :
- **Indépendants** : Chaque test peut s'exécuter seul
- **Reproductibles** : Résultats cohérents
- **Rapides** : Exécution en moins de 5 minutes par plateforme
- **Informatifs** : Logs détaillés en cas d'échec