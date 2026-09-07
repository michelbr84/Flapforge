_Idioma:_ [[English|Game-Modes-and-Difficulty]] · **Português (Brasil)**

# Modos de jogo, dificuldade e prestígio

Uma partida é definida por três escolhas: o modo (de onde vem a semente), o mundo
([[Mundos e chefes|Mundos-e-Chefes-(pt-BR)]]) e o nível de dificuldade. Esta página cobre os
quatro modos, os três níveis e o que acontece no nível 25, quando a tela Perfil oferece um
prestígio. O modo de demonstração, que toca quando você deixa a tela inicial parada, fica no fim.

## Onde o modo é escolhido

A linha Modo fica na tela Aves, ao lado das linhas Mundo e Dificuldade
([[Aves e habilidades|Aves-e-Habilidades-(pt-BR)]]). Padrão e Com semente estão sempre na lista;
Diária aparece quando o jogo tem um relógio, e enquanto `feature:seeded_runs` ainda está
bloqueado tanto Com semente quanto Diária avisam, com a condição de desbloqueio. A linha sob o
modo diz o que Jogar iniciaria: "Uma semente nova a cada partida", "Repete a semente 1234", ou o
mundo, o nível e as cartas da diária de hoje. Os desafios não estão nessa linha: eles são jogados
pela aba Desafios da tela Metas.

INICIAR PARTIDA na [[Tela inicial|Tela-Inicial-(pt-BR)]] joga a ave, o mundo, o nível e o modo
selecionados, e mostra "mundo • dificuldade" sob o botão para você sempre saber o que vai abrir.

## Padrão

Uma semente nova e aleatória a cada partida. É o modo padrão e aquele em torno do qual toda a
metaprogressão foi calibrada: cada condição de desbloqueio, preço e multiplicador de recompensa
pressupõe partidas no modo Padrão e no nível Normal.

## Com semente

Repete a semente da última partida que o seu perfil concluiu, então uma partida pode ser refeita
exatamente sobre os obstáculos que mataram você, ou compartilhada como "supere meus 63 portões
nesta semente". O HUD mostra "semente 1234" durante a partida e o resumo a repete. Com semente e
Diária abrem juntos com o recurso Partidas com Semente (`feature:seeded_runs`): chegue ao nível 5
ou compre-o por 100 moedas na aba Recursos da Loja
([[Loja e melhorias|Loja-e-Melhorias-(pt-BR)]]). Enquanto ele está bloqueado, o seletor avisa e
Jogar recorre a uma partida padrão. No desktop, `--seed N` inicia uma execução numa semente
escolhida.

## Diária

Uma partida por dia UTC para o planeta inteiro, sem servidor nenhum:

* **A data é a semente inteira.** A semente é `fnv1a("daily:" + yyyy-MM-dd)`, então dois
  jogadores em lados opostos do planeta recebem o mesmo desafio no mesmo dia UTC.
* **O sorteio vem do que você possui.** A partir dessa semente o jogo sorteia um mundo que você
  desbloqueou, um nível entre Normal e Difícil dentre os que você desbloqueou, e dois
  modificadores forçados compatíveis entre si e com o mundo e o nível. O sorteio é uniforme, sem
  peso por raridade: uma diária é uma configuração fixa, não uma oferta. Os modificadores
  forçados da diária não exigem o recurso Modificadores de Partida; só a escolha no meio da
  partida exige.
* **O sorteio fica congelado para a data.** Na primeira vez em que a diária é vista ou jogada, o
  perfil registra data, semente, mundo, nível e cartas. Desbloquear um mundo novo na hora do
  almoço não muda a partida que você treinou de manhã; só o contador de tentativas e o recorde de
  portões mudam.
* **Ela paga mais.** Uma partida diária rende ×1,25 moedas sobre a fórmula normal, mostrado na
  linha "Multiplicador diário" do resumo da partida.
* **Repetir mantém a semente.** A repetição instantânea da faixa de fim de jogo joga a mesma
  diária e só conta mais uma tentativa. As estatísticas do Perfil contam as suas partidas diárias,
  e a linha do modo diz "melhor 12 portões, tentativa 3" ou "ainda não jogada hoje".

As conquistas Voador Diário e Sete Dias premiam a primeira e a sétima diária jogadas.

## Desafio

As sete partidas especiais da aba Desafios da tela Metas. Um desafio traz o próprio mundo, o
próprio nível, as próprias regras, as próprias cartas forçadas e, no Chefe do Corredor, o próprio
chefe; ele pode ser jogado com o mundo desbloqueado ou não, e usa a ave, a paleta e o equipamento
do seu perfil sob essas regras. Concluir um pela primeira vez paga moedas e, na maioria deles, um
desbloqueio: três paletas de aves, a habilidade Invulnerabilidade (Uma Vida I) e o nível Pesadelo
(Chefe do Corredor). A lista completa, com objetivos e recompensas, está em
[[Desafios e metas|Desafios-e-Metas-(pt-BR)]].

## Níveis de dificuldade

Um nível se empilha sobre qualquer modo, exceto onde um desafio fixa o seu. Ele é escolhido na
linha Dificuldade da tela Aves ou na linha Dificuldade da tela Escolha um mundo (a captura está
em [[Mundos e chefes|Mundos-e-Chefes-(pt-BR)]]); no desktop, `--tier <id>` escolhe o nível para
uma execução.

| `id` | Nome | Efeitos | Flags | Multiplicador de recompensa | Desbloqueado por |
| --- | --- | --- | --- | --- | --- |
| `normal` | Normal | — | — | ×1,0 | disponível desde o início |
| `hard` | Difícil | velocidade do mundo ×1,10, tamanho do vão ×0,92 | — | ×1,5 | 40 portões numa partida, 400 portões no perfil todo, ou o nó Prova de Fogo da árvore Economia |
| `nightmare` | Pesadelo | velocidade do mundo ×1,20, tamanho do vão ×0,85 | todos os obstáculos se movem, teto letal | ×2,5 | o desafio Chefe do Corredor ou o nível 20 |

Nas palavras do jogo, Difícil é "Rolagem mais rápida, vãos menores, recompensas maiores" e
Pesadelo é "Tudo se move, o teto mata, o pagamento dobra". O multiplicador se aplica à
recompensa inteira em moedas da partida e aparece na linha "Multiplicador de dificuldade" do
resumo. Os efeitos do nível se multiplicam pela curva e pelos efeitos do próprio mundo: o Céu de
Tempestade no Pesadelo rola a ×1,15 × ×1,20 antes mesmo de a curva começar. As conquistas Dez no
Difícil e Dez no Pesadelo premiam 10 portões em cada nível.

> **Dica:** Prova de Fogo custa 400 moedas e não faz nada além de desbloquear o Difícil. Se você
> já passou 40 portões numa partida, a tela de melhorias marca o nó como "Já desbloqueado" e
> recusa a compra, então nada é desperdiçado.

## Prestígio

No nível 25, a tela **Perfil**, atrás do cartão do jogador na tela inicial, oferece um prestígio.
O painel lista o que o perfil acumulou, o que um prestígio redefiniria e o que ele mantém, e o
botão **Prestigiar** precisa de dois toques: o primeiro o arma com "Confirmar o prestígio?", o
segundo executa. Abaixo do nível 25 o painel diz "O prestígio abre no nível 25"; depois do quinto
prestígio, diz "O limite de prestígios foi alcançado".

| Redefine | Mantém |
| --- | --- |
| moedas, XP e o nível (de volta a 1) | todas as aves que você possui |
| todos os nós de melhoria e os níveis das habilidades | todas as paletas (cosméticos) que você possui |
| o limite de nível das habilidades (de volta a 2) e o bônus de espaços passivos (de volta a 0) | todas as conquistas, com a data |
| mundos, níveis, árvores, habilidades, desafios e recursos: tudo conquistado de novo | as estatísticas de todo o tempo |
| os registros de desafio e o sorteio da diária | a contagem de prestígios e o bônus dela |

O que você ganha, e guarda para sempre:

* **+5% de moedas por prestígio** em toda partida seguinte, acumulando até +25% no quinto. O
  Perfil mostra isso como "Bônus permanente: +5% de moedas".
* **A paleta dourada Prestígio** da ave com que você prestigiou. Toda ave tem uma; faça o
  prestígio com uma ave diferente a cada vez para colecioná-las.
* **O distintivo.** O cartão do jogador na tela inicial e o cabeçalho do Perfil trazem
  "Prestígio ×1" (ou mais), e a cena da forja na tela inicial mostra detalhes dourados na bigorna
  a partir do primeiro prestígio.

O perfil também congela uma linha de base dos seus totais naquele momento. As condições de
desbloqueio que contam partidas, portões, moedas ganhas ou chefes vencidos passam a ler "desde o
prestígio", então a subida é real, mas nada já conquistado é concedido duas vezes, e as condições
por conquista ou por recorde continuam funcionando. Um aviso confirma a gravação, e a seleção
volta para Asa-forjada (Forgewing), Campos Verdes (Green Fields) e Normal.

## Modo de demonstração

Deixe a tela inicial parada por vinte segundos e um bot joga uma partida de demonstração atrás
dela, escurecida sob a interface da tela inicial, numa semente fixa de demonstração. Ela roda sem
o seu perfil, então nunca ganha, gasta ou muda nada. Qualquer entrada (uma tecla, um clique, uma
mudança de foco) traz o jogo de volta e zera o contador de inatividade.

Páginas relacionadas: [[Jogando uma partida|Jogando-uma-Partida-(pt-BR)]] para o HUD e o resumo
da partida, [[Desafios e metas|Desafios-e-Metas-(pt-BR)]] para a tela Metas,
[[Perguntas frequentes|Perguntas-Frequentes-(pt-BR)]] para as dúvidas comuns sobre a diária e o
prestígio.
