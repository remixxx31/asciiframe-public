# Thèmes Personnalisés AsciiFrame

Ce répertoire contient des exemples de thèmes personnalisés pour AsciiFrame.

## Structure

```
themes/
├── html/
│   └── custom-example.css    # Exemple de thème CSS pour HTML
├── pdf/
│   └── custom-example.yml    # Exemple de thème PDF
└── README.md                 # Ce fichier
```

## Créer un thème HTML personnalisé

1. Copiez le fichier `html/custom-example.css`
2. Renommez-le selon vos besoins (ex: `mon-theme.css`)
3. Modifiez les styles CSS selon vos préférences
4. Dans `config.yml`, configurez :
   ```yaml
   theme:
     customCssPath: themes/html/mon-theme.css
   ```

Le fichier d'exemple contient des commentaires détaillés pour vous guider.

## Créer un thème PDF personnalisé

1. Copiez le fichier `pdf/custom-example.yml`
2. Renommez-le selon vos besoins (ex: `mon-theme.yml`)
3. Modifiez les propriétés YAML selon vos préférences
4. Dans `config.yml`, configurez :
   ```yaml
   theme:
     customPdfThemePath: themes/pdf/mon-theme.yml
   ```

Le fichier d'exemple contient des commentaires détaillés pour vous guider.

## Priorité des thèmes

- **HTML** : Si `customCssPath` est défini, il est utilisé en priorité sur le thème prédéfini
- **PDF** : Si `customPdfThemePath` est défini, il est utilisé en priorité sur le thème prédéfini

## Documentation

- [Documentation CSS pour HTML](https://docs.asciidoctor.org/asciidoctor/latest/html-backend/stylesheet-modes/)
- [Documentation thèmes PDF](https://docs.asciidoctor.org/pdf-converter/latest/theme/)