#!/bin/sh
# Hook Xcode Cloud, exécuté juste après le clone, avant le build.
# Fait échouer tôt si spec/games/ et Catalog/GameDefinitions/ ont divergé (voir docs/02-architecture.md),
# ou si un fichier Swift ne respecte pas swift-format (doc 10 « Qualité de code »).
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/Scripts/check-spec-sync.sh"
"$ROOT/Scripts/lint.sh"
