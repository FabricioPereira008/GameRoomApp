#!/usr/bin/env sh
# Copia os contratos do GameRoom (dono dos schemas) para os testes do app.
# Uso: ./scripts/sync-contracts.sh [caminho-do-GameRoom]
set -e
SRC="${1:-$(dirname "$0")/../../GameRoomLog_Gravity}/docs/contracts"
DST="$(dirname "$0")/../app/src/test/resources/contracts"
[ -d "$SRC" ] || { echo "Contratos não encontrados em $SRC" >&2; exit 1; }
mkdir -p "$DST/examples"
cp "$SRC"/*.schema.json "$DST/"
cp "$SRC"/examples/*.json "$DST/examples/"
echo "Contratos copiados de $SRC. Rode ./gradlew testDebugUnitTest para validar."
