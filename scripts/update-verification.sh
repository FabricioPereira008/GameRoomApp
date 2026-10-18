#!/usr/bin/env sh
# Regera gradle/verification-metadata.xml (SHA-256 de cada dependência e plugin) rodando as mesmas
# tarefas da CI e do release. Rode depois de mudar versões (inclusive nos PRs do Dependabot) e revise
# o diff: só devem aparecer as bibliotecas que você atualizou.
#
# Usa um cache do Gradle vazio, como o de um runner da CI: com o cache do dia a dia, o Gradle deixa de
# registrar arquivos que já estavam baixados (BOMs, POMs pais) e a CI falha na verificação.
set -e
cd "$(dirname "$0")/.."
GRADLE_USER_HOME="$(mktemp -d)"
export GRADLE_USER_HOME
trap 'rm -rf "$GRADLE_USER_HOME"' EXIT
./gradlew --write-verification-metadata sha256 --no-configuration-cache \
    testDebugUnitTest lintDebug assembleDebug assembleRelease
echo "gradle/verification-metadata.xml atualizado."
