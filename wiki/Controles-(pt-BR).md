_Idioma:_ [[English|Controls]] · **Português (Brasil)**

# Controles

Flapforge mantém de propósito um único botão como controle principal: a dificuldade vem do
tempo, do posicionamento, da leitura dos obstáculos e das decisões de combinação, não de
sequências de comandos. Um segundo comando dispara a habilidade ativa equipada, e todo o resto é
navegação de menu. Teclado, mouse e toque funcionam em todas as telas.

## Teclas e mouse

| Entrada | Ação |
| --- | --- |
| `Space`, `Up arrow`, botão esquerdo do mouse | Bater as asas |
| `X`, `Shift`, botão direito do mouse | Usar a habilidade ativa equipada |
| `Esc` | Pausar a partida / voltar uma tela / na tela inicial, duas vezes para sair |
| `Enter` | Confirmar o item em foco |
| `M` | Silenciar / reativar o áudio |
| `F3` | Mostrar ou esconder a sobreposição de depuração (taxa de ticks, tempo de quadro, semente) |
| `F11` | Alternar a tela cheia sem bordas |

Bater as asas é uma borda: um toque é uma batida, por mais tempo que você segure a tecla, a não
ser que **Segurar para bater asas** esteja ligado (veja abaixo). No momento READY antes de uma
partida, a primeira batida é o que a começa.

## Menus

Toda tela pode ser usada com qualquer um dos dispositivos:

- as setas ou `Tab` movem o anel de foco, `Enter` ou `Space` ativam o controle em foco e `Esc`
  volta uma tela;
- todo controle também responde a passar o mouse, clicar e tocar;
- na tela inicial, `Esc` mostra "Pressione de novo para sair" e um segundo toque fecha o jogo. No
  desktop, Configurações › Sobre também tem uma linha Sair.

Listas e abas (a Loja, as abas de Metas, as árvores de melhorias) usam o mesmo anel de foco, e as
configurações de tamanho do texto e de daltonismo valem para todas — veja
[[Configurações e acessibilidade|Configuracoes-e-Acessibilidade-(pt-BR)]].

## Reatribuir teclas

Todas as sete teclas da tabela acima podem ser reatribuídas em Configurações › Controles:
selecione a ação e a tela espera com "Pressione uma tecla, ou Esc para cancelar". As setas, o
`Esc` de voltar e os botões do mouse são fixos. As teclas ficam em `settings.json`, na chave
`keyBindings`, que traz exatamente essas sete ações; "Restaurar padrões" devolve as originais.

## Durante uma partida

| Situação | Entrada |
| --- | --- |
| Voando | bater as asas; habilidade; `Esc` pausa |
| Pausado | os botões **Continuar** e **Menu**; `Esc` continua; um clique ou toque fora dos botões também continua |
| Escolha de modificador | as setas comparam as cartas, `Enter` (ou `Space`) leva a carta em foco, `Esc` ou o botão **Pular esta escolha** pula |
| Chefe | nada de novo: atravesse os padrões até o cronômetro zerar |

Enquanto as cartas estão na tela, uma batida, um toque na habilidade ou uma pausa não chegam à
partida, então esmagar `Space` sobre as cartas não joga o pássaro num cano que ele não vê; a
batida que você estava segurando quando a escolha abriu também não leva uma carta sozinha. Veja
[[Modificadores e sinergias|Modificadores-e-Sinergias-(pt-BR)]].

## A faixa de fim de jogo e o De novo

Quando o pássaro morre, a faixa de fim de jogo aparece com três botões: **De novo**, **Resumo** e
**Menu**.

- `Space` (ou um clique esquerdo, ou um toque fora dos botões) recomeça na hora com uma semente
  nova.
- `Enter` abre o resumo da partida.
- `Esc` volta à tela inicial.

As recompensas são creditadas no instante em que a partida termina, então jogar de novo nunca as
perde. O De novo da Diária mantém a semente e só conta a tentativa; veja
[[Modos de jogo e dificuldade|Modos-de-Jogo-e-Dificuldade-(pt-BR)]]. A faixa e o resumo estão
descritos em [[Jogando uma partida|Jogando-uma-Partida-(pt-BR)]].

## `M`, `F3` e `F11` ficam guardados

Os três atalhos funcionam em todas as telas e não são interruptores descartáveis: cada um vira a
configuração correspondente (silenciar, a sobreposição de depuração, tela cheia) em
`settings.json`, aplica e grava, então o jogo abre do jeito que você deixou e a tela de
Configurações sempre mostra o que está valendo.

## Tempo de resposta da entrada

A entrada é amostrada por tick da simulação (60 Hz), não por quadro: um toque mais curto que um
quadro nunca se perde, e a repetição automática da tecla nunca produz uma batida a mais. Uma tecla
ainda segurada quando a janela perde o foco é solta de forma limpa, e uma tecla segurada durante
a troca de tela cheia (`F11` recria a janela) não vira um segundo toque.

## Toque (Android)

- Um toque em qualquer ponto do campo de jogo bate as asas.
- **De novo** / **Resumo** / **Menu** na faixa de fim de jogo e **Continuar** / **Menu** no
  painel de pausa são botões de verdade; um toque fora deles ainda recomeça (fim de jogo) ou
  continua (pausa).
- Todo controle de menu responde ao toque do mesmo jeito que responde a um clique.
- Não há linha Sair no Android; saia do aplicativo do jeito que o sistema faz.
- A imagem preenche telas altas de ponta a ponta; a configuração **Preencher a tela** (ligada
  por padrão) devolve as barras pretas quando desligada.

## Segurar para bater asas

Configurações › Jogo › **Segurar para bater asas** faz o comando de bater, quando segurado, bater
sem parar — uma batida a cada 24 ticks — em vez de uma por toque. É uma opção de acessibilidade,
gravada como qualquer outra configuração; veja
[[Configurações e acessibilidade|Configuracoes-e-Acessibilidade-(pt-BR)]].

> **Dica:** o HUD e a sobreposição de `F3` mostram a semente da partida que você está voando. O
> modo Com semente repete a semente da última partida concluída, então os canos que mataram você
> podem ser voados de novo exatamente nos mesmos lugares — veja
> [[Modos de jogo e dificuldade|Modos-de-Jogo-e-Dificuldade-(pt-BR)]].
