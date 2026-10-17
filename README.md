# GameRoom Portátil

App Android para registrar, offline, o que você joga no portátil (Jogando, Zerado, Platinado, com data, horas, nota, dificuldade e observação) e levar esses registros para o [GameRoomLog](../GameRoomLog_Gravity) no PC.

Os dois projetos só compartilham **um contrato JSON versionado**, cujo dono é o GameRoom (`docs/contracts/` lá). O app não conhece o banco nem a API interna do GameRoom.

## Fluxo

1. **(Opcional) Catálogo:** no GameRoom, *Configurações → App do Portátil → Exportar catálogo*. No app, *⋮ → Importar catálogo do GameRoom*. Assim o app sugere os títulos e plataformas que já existem no PC e vincula o jogo pelo id.
2. **Registrar:** *Novo jogo* → título e plataforma (texto livre, com sugestões) → *Jogando / Zerado / Platinado*.
3. **Exportar:** o botão de upload (ou *⋮ → Salvar/Compartilhar exportação*) gera `gameroom-inbox-AAAAMMDD-HHMM.json`. O arquivo sempre leva todos os registros; o GameRoom ignora os que já recebeu.
4. **Importar no PC:** *Configurações → App do Portátil → Importar eventos do portátil*, depois revise cada jogo.

Editar um registro já exportado gera um UUID novo, então a correção chega ao GameRoom como um evento novo. Apagar no app não desfaz nada no GameRoom.

## Build

Requer JDK 17 e Android SDK (platform 35).

```sh
export JAVA_HOME=~/.local/jdk-17 ANDROID_HOME=~/Android/Sdk
./gradlew testDebugUnitTest        # testes (inclui validação contra os schemas do GameRoom)
./gradlew assembleRelease          # APK em app/build/outputs/apk/release/
adb install -r app/build/outputs/apk/release/app-release.apk
```

O APK de release é assinado com a chave de debug (uso pessoal, instalação direta).

## Contrato

`app/src/test/resources/contracts/` é uma cópia dos schemas do GameRoom. Quando o GameRoom mudar o contrato, rode `./scripts/sync-contracts.sh` e os testes. Regras: campos novos são opcionais, campos desconhecidos são ignorados, e `version` só sobe em mudanças incompatíveis.

## Estrutura

- `contract/`: DTOs e (de)serialização dos formatos `gameroom-inbox` e `gameroom-catalog`
- `domain/`: tipos de evento, regra de UUID na edição, sugestões e parsing de números
- `data/`: Room (jogos, eventos, catálogo) e repositório (exportação/importação)
- `ui/`: Compose (lista de jogos, jogo com registros, editores)
