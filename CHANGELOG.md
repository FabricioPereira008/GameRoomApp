# Changelog

Todas as alterações notáveis neste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/),
e este projeto adere ao [Semantic Versioning](https://semver.org/lang/pt-BR/).

## [0.2.0] - 2026-10-17
### Modificado
- O botão de exportar abre uma escolha entre **Salvar arquivo** e **Compartilhar**, com uma explicação de cada opção. As opções duplicadas saíram do menu ⋮.
- **Compartilhar** só marca os registros como exportados quando você escolhe um app no menu do Android. Cancelar não marca nada. Depois do envio aparece "Enviado via …" com **Desfazer**, para o caso de o envio não ter sido concluído.
- Exportar marca como exportados apenas os registros novos; o arquivo continua levando todos.

## [0.1.0] - 2026-10-17
### Adicionado
- Primeira versão do GameRoom Portátil.
- Cadastro de jogos com título e plataforma em texto livre, com sugestões do que já foi digitado e do catálogo do GameRoom.
- Registros de **Jogando**, **Zerado** e **Platinado** com data. Zerado e Platinado também aceitam horas totais, nota, dificuldade e observação.
- Exportação no formato `gameroom-inbox` v1 (salvar arquivo ou compartilhar), para importar em *Configurações → App do Portátil* no GameRoom 1.0.0+.
- Importação do catálogo `gameroom-catalog` v1 exportado pelo GameRoom, que vincula o jogo pelo id.
- Editar um registro já exportado gera um evento novo, para a correção chegar ao GameRoom.
- Funciona 100% offline (Room), com o tema escuro do GameRoom.
