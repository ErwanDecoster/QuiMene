#!/bin/sh
# swift-format en mode lint (doc 10 « Qualité de code ») — aucune correction, échoue si un
# fichier ne respecte pas la configuration par défaut d'Apple (.swift-format à la racine).
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
xcrun swift-format lint --recursive --strict --configuration "$ROOT/.swift-format" \
    "$ROOT/CaCompteKit/Sources" \
    "$ROOT/CaCompteKit/Tests" \
    "$ROOT/App"
