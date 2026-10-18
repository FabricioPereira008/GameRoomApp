# Changelog

Todas as alterações notáveis neste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/),
e este projeto adere ao [Semantic Versioning](https://semver.org/lang/pt-BR/).

## [Não publicado]
### Adicionado
- **Platinado com data do zeramento:** o registro de Platinado tem a data da platina e a do zeramento (sugerida a partir do último zeramento registrado). Quando são dias diferentes, o GameRoom recebe um Zerado e depois o Platinado.
- Opção **Incluir os já exportados** na exportação, para reenviar um arquivo perdido.
- Aviso na tela inicial quando há registros pendentes há mais de 7 dias.
- Aviso ao renomear um jogo que já tem registros exportados.
- **Desfazer** depois de apagar um jogo; a confirmação avisa quantos registros ainda não foram exportados.
- O menu mostra o tamanho do catálogo e a data em que o GameRoom o exportou.

### Modificado
- A exportação leva só os registros novos (antes levava todos), respeitando o limite de 5000 eventos do GameRoom; o que não couber vai na próxima.
- Os caminhos indicados no app e no README seguem o GameRoom 1.3: **Importar → App do portátil**.
- APK menor (R8 e remoção de recursos não usados). Dependências atualizadas, compilando com o Android 17 e mirando o Android 16.
- Backup do Android configurado explicitamente para o banco de dados.
- O editor de registro abre em tela cheia, e os botões de tipo quebram linha em telas estreitas.
- Importar um catálogo desfaz o vínculo dos jogos que não estão mais no GameRoom (o id poderia passar a ser de outro jogo).
- Sugestões de título mais leves em catálogos grandes; as telas deixam de observar o banco com o app em segundo plano.
- Arquivos temporários de compartilhamento com mais de um dia são apagados.

### Corrigido
- Título, plataforma e observação acima do limite do GameRoom (200, 100 e 5000 caracteres) eram descartados por ele sem aviso e já saíam do app como exportados. Os campos agora têm esses limites.
- "NaN" ou "Infinity" em horas, nota ou dificuldade impediam qualquer exportação.
- Ao compartilhar, os registros podiam deixar de ser marcados como exportados se a tela fosse recriada (rotação, app encerrado pelo sistema) enquanto o menu do Android estava aberto.
- Abrir um jogo que não existe mais mostrava uma tela vazia; agora volta para a lista.

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
