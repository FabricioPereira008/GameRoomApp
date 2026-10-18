#!/usr/bin/env sh
# Regera gradle/verification-metadata.xml (SHA-256 de cada dependência e plugin) rodando as mesmas
# tarefas da CI e do release. Rode depois de mudar versões (inclusive nos PRs do Dependabot) e revise
# o diff: só devem aparecer as bibliotecas que você atualizou.
set -e
cd "$(dirname "$0")/.."
./gradlew --write-verification-metadata sha256 --no-configuration-cache \
    testDebugUnitTest lintDebug assembleDebug assembleRelease
echo "gradle/verification-metadata.xml atualizado."
