# GameRoom Portátil

[![CI](https://github.com/FabricioPereira008/GameRoomApp/actions/workflows/ci.yml/badge.svg)](https://github.com/FabricioPereira008/GameRoomApp/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/FabricioPereira008/GameRoomApp)](https://github.com/FabricioPereira008/GameRoomApp/releases/latest)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
[![Licença MIT](https://img.shields.io/badge/licen%C3%A7a-MIT-blue)](LICENSE)

App Android complementar ao **[GameRoomLog](https://github.com/FabricioPereira008/GameRoomLog)**. Ele registra, offline, o que você joga no portátil (em viagem, por exemplo) e depois leva esses registros para o GameRoom no PC.

O app **não** é uma cópia do GameRoom. Ele guarda só o essencial: título, plataforma, status, data, horas, nota, dificuldade e observação. Os dois projetos evoluem de forma independente e só compartilham um contrato JSON versionado.

## Download

Baixe o `GameRoomPortatil-<versão>.apk` na [página de releases](https://github.com/FabricioPereira008/GameRoomApp/releases/latest) e instale no aparelho (é preciso permitir a instalação de fontes desconhecidas). Requer Android 8.0 ou superior.

| App | GameRoom necessário |
|---|---|
| 0.1.x – 0.2.x | 1.0.0 ou superior |

## Como usar

1. **(Opcional) Catálogo:** no GameRoom, abra *Configurações → App do Portátil → Exportar catálogo para o portátil*. No app, toque em *⋮ → Importar catálogo do GameRoom*. O app passa a sugerir os títulos e plataformas que você já tem no PC.
2. **Registrar:** toque em *Novo jogo*, informe título e plataforma, e registre *Jogando*, *Zerado* ou *Platinado*.
3. **Exportar:** o botão de upload oferece *Salvar arquivo* ou *Compartilhar* e gera um arquivo `gameroom-inbox-AAAAMMDD-HHMM.json`. Leve-o ao PC por Syncthing, Drive, cabo, e-mail…
4. **Importar no PC:** em *Configurações → App do Portátil → Importar eventos do portátil*, revise cada jogo e aplique.

Cada exportação leva todos os registros; o GameRoom ignora os que já recebeu, então reimportar não duplica nada. Editar um registro já exportado faz com que ele chegue como uma correção. Apagar no app não desfaz nada no GameRoom.

## Contrato com o GameRoom

O dono do contrato é o GameRoom ([`docs/contracts`](https://github.com/FabricioPereira008/GameRoomLog/tree/main/docs/contracts)):

- `gameroom-inbox` v1: eventos que o app exporta.
- `gameroom-catalog` v1: catálogo opcional que o GameRoom exporta para o app.

Uma cópia dos schemas fica em `app/src/test/resources/contracts/`, e os testes validam a exportação do app contra ela. Quando o contrato mudar no GameRoom, rode `./scripts/sync-contracts.sh` e os testes. Regras: campos novos são opcionais, campos desconhecidos são ignorados, e `version` só sobe em mudanças incompatíveis.

## Desenvolvimento

Kotlin, Jetpack Compose (Material 3), Room e kotlinx.serialization. Requer JDK 17 e Android SDK (platform 35).

```sh
export JAVA_HOME=/caminho/do/jdk-17 ANDROID_HOME=~/Android/Sdk
./gradlew testDebugUnitTest   # testes unitários e de contrato
./gradlew assembleRelease     # APK em app/build/outputs/apk/release/
adb install -r app/build/outputs/apk/release/app-release.apk
```

O build de release é assinado com a chave definida em `~/.android/gameroomapp-release.properties` (ou em `-Pgameroomapp.signing=<arquivo>`):

```properties
storeFile=/caminho/gameroomapp-release.jks
storePassword=...
keyAlias=gameroomapp
keyPassword=...
```

Sem esse arquivo, o release é assinado com a chave de debug, que só serve para testes. Os APKs publicados nas releases usam sempre a mesma chave de release, porque o Android só instala uma atualização por cima se a assinatura for igual.

```
app/src/main/java/com/gameroom/portatil/
├── contract/   DTOs e (de)serialização dos formatos do GameRoom
├── domain/     tipos de evento, regra de UUID na edição, sugestões, números
├── data/       Room (jogos, eventos, catálogo) e repositório
└── ui/         telas Compose
```

## Licença

[MIT](LICENSE)
