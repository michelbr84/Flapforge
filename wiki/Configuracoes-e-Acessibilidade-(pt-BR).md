_Idioma:_ [[English|Settings-and-Accessibility]] · **Português (Brasil)**

# Configurações e acessibilidade

A tela Configurações abre pela engrenagem no alto da [[Tela inicial|Tela-Inicial-(pt-BR)]]. Toda
linha é aplicada **na hora** — não há botão Aplicar e nada exige reiniciar — e toda mudança é
gravada em `settings.json` imediatamente, então o jogo abre do jeito que você o deixou. As linhas
não cabem na tela, por isso a lista rola; os dois botões que você precisa alcançar sempre,
**Restaurar padrões** e **Voltar**, ficam em uma barra fixa no rodapé.

![A tela Configurações](images/settings.png)

*A tela Configurações: a linha de idioma e depois as seções Som, Vídeo, Jogo, Controles e Sobre,
com Restaurar padrões e Voltar fixos embaixo (interface em inglês).*

## Idioma

A primeira linha é **Idioma**, com três opções: **Automático**, que segue o idioma do sistema e
mostra o que resolveu ("Automático (Português (Brasil))"), **Inglês** e **Português (Brasil)**.
A troca é imediata: o aviso "Idioma: …" confirma e a tela inicial atrás se traduz na hora. A opção
de linha de comando `--lang en` / `--lang pt_BR` sobrepõe esta linha só por uma execução (veja
[[Primeiros passos|Primeiros-Passos-(pt-BR)]]).

## Som

| Linha | Padrão | Observações |
| --- | --- | --- |
| Volume geral | 80 % | ganho aplicado a todas as vozes |
| Volume dos efeitos | 100 % | efeitos sonoros |
| Volume da música | 60 % | o loop chiptune de cada mundo |
| Silenciar tudo | desligado | a mesma chave que a tecla `M` alterna em qualquer tela |

Todo o áudio é sintetizado em tempo de execução; não há arquivos de som para instalar. Se nenhum
dispositivo de som puder ser aberto, o jogo roda exatamente igual, em silêncio (veja a opção
`--no-audio` nas [[Perguntas frequentes|Perguntas-Frequentes-(pt-BR)]]).

## Vídeo

| Linha | Padrão | O que faz |
| --- | --- | --- |
| Tela cheia | desligado | tela cheia sem bordas; `F11` alterna em qualquer tela |
| Escala inteira | desligado | prende o campo de jogo de 420×640 a escalas de pixel inteiro (1×, 2×, 3× …) em vez de esticar até a janela |
| Preencher a tela | ligado | em janelas mais altas que 420:640, pinta céu e chão sobre o que seriam faixas pretas; puramente cosmético, o jogo continua dentro do campo |
| Limite de quadros | 60 fps | 60 fps, 120 fps, 144 fps, Sem limite ou Igual à tela; a simulação roda sempre a 60 Hz, o limite só cadencia a renderização |
| Suavização | ligado | permite suavização bilinear quando a escala não é um número inteiro |
| Mostrar tempo de quadro | desligado | o painel de depuração (taxa de ticks, tempo de quadro, semente) que `F3` alterna |

## Jogo

| Linha | Padrão | O que faz |
| --- | --- | --- |
| Alto contraste | desligado | contornos mais fortes nos perigos e na ave, painéis do HUD opacos e um teto para a escuridão do mundo, para que mundos velados continuem legíveis |
| Paleta para daltonismo | Nenhuma | Protanopia, Deuteranopia ou Tritanopia recolorem toda paleta de mundo e as cores semânticas (avisos de perigo, moedas, chamas) mantendo separadas as luminâncias de perigo, aviso e moeda |
| Reduzir flashes | ligado | respeitado por relâmpagos, avisos e partículas; também limita o brilho pulsante do INICIAR PARTIDA na tela inicial |
| Tamanho do texto | 1,00 | um controle deslizante de 0,75 a 1,50 em passos de 0,05; toda tela se reorganiza |
| Segurar para bater asas | desligado | segurar o comando de bater asas bate continuamente (a cada 24 ticks) em vez de uma vez por toque |

Estas cinco linhas, mais os controles e as linhas de som, são as configurações de acessibilidade.
Todas são aplicadas na hora e todas são gravadas, então uma paleta para daltonismo ou um texto
maior definidos uma vez ficam definidos.

## Controles

A seção Controles tem uma linha por ação que pode ser remapeada, mostrando as teclas atuais:

| Ação | Teclas padrão |
| --- | --- |
| Bater asas | `Space`, `Up` (botão esquerdo do mouse, sempre) |
| Habilidade | `X`, `Shift` (botão direito do mouse, sempre) |
| Pausar | `Esc` |
| Confirmar | `Enter` |
| Silenciar | `M` |
| Painel de depuração | `F3` |
| Tela cheia | `F11` |

Pressione uma linha e a tela pede "Pressione uma tecla, ou Esc para cancelar"; a próxima tecla
vira o atalho, e um aviso confirma ("Bater asas: F"). Uma tecla que outra ação já usa é recusada
com "*tecla* já está em uso por *ação*". As setas, o `Esc` para voltar e os botões do mouse são
fixos e não podem ser remapeados. A lista completa do que cada tecla faz está em
[[Controles|Controles-(pt-BR)]].

## Sobre

A última seção é informativa: a versão do jogo (`v0.2.0`), o Java em que ele está rodando
("Java 17", por exemplo) e um lembrete das teclas globais, "F3 depuração   F11 tela cheia". No
computador ela também traz a linha **Sair** — a outra saída do jogo além de pressionar `Esc` duas
vezes na tela inicial. No Android a linha não existe; o aplicativo é fechado pelo gesto Voltar do
próprio sistema.

## Teclas globais

`M` (silenciar), `F3` (painel de depuração) e `F11` (tela cheia) funcionam em qualquer tela,
inclusive dentro de uma partida. Elas não são chaves do motor: cada uma alterna a linha
correspondente desta tela e a grava, então a tela Configurações sempre mostra o que está em vigor
e o estado sobrevive a um reinício.

## Restaurar padrões

O botão **Restaurar padrões** do rodapé devolve todas as linhas — idioma, volumes, vídeo, opções
de jogo e controles — aos valores das tabelas acima, aplica tudo na hora e confirma com
"Configurações restauradas ao padrão".

## O arquivo de configurações

As configurações ficam em `settings.json`, dentro da pasta do perfil, ao lado do save:

| Sistema | Pasta do perfil |
| --- | --- |
| Linux / BSD | `~/.flapforge` |
| Windows | `%APPDATA%\Flapforge` |
| macOS | `~/Library/Application Support/Flapforge` |

A pasta pode ser trocada pela opção `--home DIR`, pela propriedade de sistema `flapforge.home` ou
pela variável de ambiente `FLAPFORGE_HOME`, nessa ordem de precedência. Toda gravação é à prova de
travamento: o arquivo é escrito com um nome temporário, descarregado e renomeado atomicamente.

O arquivo carrega um campo `version` (hoje `1`). Um `settings.json` cuja versão difere da versão
do build **não é carregado**: os padrões são restaurados e o arquivo antigo é mantido ao lado como
`settings.v<N>.json` (`settings.v<N>-2.json`, `-3` … quando já existe um), com um aviso que nomeia
o arquivo. Um arquivo sem nenhuma chave `version` é tratado como chave ausente, não como
incompatibilidade, então um arquivo editado à mão mantém seus valores. `keyBindings` guarda
exatamente as sete ações remapeáveis; as setas de foco e Voltar nunca são gravados.

> **Dica:** se uma configuração não for lembrada entre execuções, confira se a pasta do perfil
> permite escrita — uma gravação que falha mostra o aviso "Não foi possível salvar" em vez de
> travar o jogo.
