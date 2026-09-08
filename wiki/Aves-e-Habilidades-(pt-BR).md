_Idioma:_ [[English|Birds-and-Abilities]] · **Português (Brasil)**

# Aves e habilidades

Toda partida começa com uma ave e um equipamento de habilidades. A ave define a física com que
você voa (gravidade, força da batida, velocidade de queda) e, na maioria dos casos, um traço
próprio; o equipamento acrescenta uma habilidade ativa e algumas passivas. As duas escolhas são
feitas na tela **Aves**, aberta pela navegação inferior da [[Tela inicial|Tela-Inicial-(pt-BR)]].
Aves e habilidades são conquistadas jogando ou compradas com moedas; preços, carteira e recusas
estão em [[Loja e melhorias|Loja-e-Melhorias-(pt-BR)]].

## As sete aves

As sete aves têm a mesma hitbox de 33 × 31. O que muda é a física, o número de espaços passivos
e o traço próprio. Nomes e descrições são os do próprio jogo (`bird.<id>.name` /
`bird.<id>.desc`); a física de referência é a da Asa-forjada (Forgewing): gravidade 1800, batida
405 e queda máxima de 1500 px/s.

| `id` | Nome | Arquétipo | Espaços passivos | Traço próprio | Conquiste com | Ou compre por |
| --- | --- | --- | --- | --- | --- | --- |
| `classic` | Asa-forjada (Forgewing) | Equilibrada | 2 | Sem modificadores: a ave com que se aprende a voar | disponível desde o início | — |
| `swift` | Zéfiro (Zephyr) | Ágil | 2 | Gravidade 2100 e batida 470: batida mais forte sob um céu mais pesado | passar 15 portões numa partida | 300 moedas |
| `heavy` | Bigorna (Anvil) | Pesada | 2 | Gravidade 2200, batida 460, e nunca cai a mais de 450 px/s | jogar 4 partidas | 200 moedas |
| `guardian` | Bico-de-ferro (Ironbeak) | Guardiã | 2 | Voa com um Escudo (Shield) inato; multiplicador de moedas −20% | jogar 3 partidas | 150 moedas |
| `gambler` | Gralha (Jackdaw) | Apostadora | 2 | +30% de pontos e +30% de moedas; vãos ×0,9; escala da hitbox +0,10 | passar 25 portões numa partida | 500 moedas |
| `mystic` | Oráculo (Oracle) | Mística | 3 | Duração das habilidades ×1,3 e recarga ×1,4 | a conquista Adepto das Habilidades (usar habilidades 50 vezes) | 600 moedas |
| `forge` | Brasa (Cinder) | Forja | 2 | +2% de pontos por portão passado (até +50%); +0,5% de batida e +1% de moedas por nível de melhoria adquirido (até +8% / +25%); batida base 385 | vencer o chefe de Campos Verdes (Green Fields) | 800 moedas |

Os arquétipos foram feitos para jogar de forma diferente, não só para ter outra aparência:

| Arquétipo | Ponto forte | Ponto fraco |
| --- | --- | --- |
| Equilibrada | Movimento previsível | Nenhuma vantagem marcante |
| Ágil | Reação rápida | Mais difícil de controlar |
| Pesada | Descida estável | Exige um timing mais preciso |
| Guardiã | Habilidade defensiva | Multiplicador de recompensa menor |
| Apostadora | Recompensas maiores | Dificuldade maior |
| Mística | Foco nas habilidades | Recargas mais longas |
| Forja | Sinergia com as melhorias | Fraca no início da partida |

A sinergia da Brasa é calculada uma vez, no início da partida, a partir do total de níveis de
melhoria que você possui (veja [[Loja e melhorias|Loja-e-Melhorias-(pt-BR)]]); a rampa dela cresce
a cada portão. Somadas às melhorias e às cartas escolhidas no meio da partida
([[Modificadores e sinergias|Modificadores-e-Sinergias-(pt-BR)]]), as aves viram combinações
reconhecíveis: Bico-de-ferro com Escudo Temperado e Escudo no nível 3 é uma combinação defensiva;
Gralha com Multiplicador de Pontos é uma combinação de economia com risco alto.

> **Dica:** o escudo do Bico-de-ferro custa um quinto das moedas, mas se paga cedo: as notas de
> balanceamento medem +96% de portões para o bot médio do jogo. É também a ave mais barata da loja.

## A tela Aves

![A tela Aves](images/birds.png)
*A tela Aves: a ave em exibição sobre a bigorna, o elenco abaixo dela e a partida em uma linha
(interface em inglês).*

A tela veste a roupa da tela inicial: o mesmo cabeçalho, o mesmo botão dourado e a mesma
navegação de rodapé, com **Aves** na placa dourada. Ela tem sete partes, de cima para baixo:

* **O cabeçalho.** O título à esquerda e suas moedas à direita. O chip de moedas abre a
  [[Loja e melhorias|Loja-e-Melhorias-(pt-BR)]], como na tela inicial.
* **A ave.** A ave que você está vendo, grande, balançando sobre uma bigorna numa ilha
  flutuante: o nome, uma linha dizendo o que ela é (`Guardiã · Selecionado`,
  `Pesada · Bloqueado`) e três selos — **Mobilidade**, **Defesa** e **Controle**, cada um de
  dez. Os três números descrevem a ave sozinha, lidos dos dados dela e das habilidades inatas:
  são eles que separam o Zéfiro (7 de mobilidade) do Bico-de-ferro (10 de defesa), e não se
  movem quando você compra uma melhoria. O que a partida realmente resolveria está no
  detalhamento atrás de **Ver detalhes**.
* **O elenco.** Um cartão por ave, numa faixa que você percorre com Esquerda/Direita, com as
  setas nas pontas ou com a roda do mouse. O cartão traz o retrato na paleta que você escolheu
  para aquela ave, o nome e o arquétipo; um cartão bloqueado fica escurecido sob um cadeado e
  diz o caminho mais barato, em palavras ou em moedas. O cartão da ave selecionada usa a mesma
  borda dourada do botão da tela inicial. Passar por um cartão mostra aquela ave acima; tocar um
  cartão adquirido seleciona a ave e salva na hora, e tocar um bloqueado sacode o cadeado dele.
* **Cores.** Uma amostra por paleta da ave, com as bloqueadas marcadas. Toda ave tem a paleta
  Padrão e uma paleta dourada **Prestígio** (concedida por um prestígio, veja
  [[Modos de jogo e dificuldade|Modos-de-Jogo-e-Dificuldade-(pt-BR)]]); as outras estão na
  tabela abaixo. Trocar de ave corrige a paleta, porque cada paleta pertence a uma ave.
* **A barra da partida.** Uma linha — `Campos Verdes · Normal · Padrão ›` — com as cores do
  mundo ao lado e, embaixo, os perigos, a dica da semente ou a configuração da diária. Ativá-la
  abre um painel com as linhas Mundo, Dificuldade e Modo e um botão **Concluir**; Esc também
  fecha. A linha Mundo lista os cinco mundos em ordem, com "Perigos: …" para um mundo adquirido
  e "Bloqueado: …" com o caminho mais barato para um bloqueado; a linha Dificuldade lista Normal,
  Difícil e Pesadelo; a linha Modo lista Padrão, Com semente e Diária. Passar para algo
  bloqueado faz a linha voltar, com um aviso. Com a **Diária** definida, as linhas Mundo e
  Dificuldade ficam somente para leitura e dizem isso: a diária escolhe por você. Veja
  [[Mundos e chefes|Mundos-e-Chefes-(pt-BR)]] e
  [[Modos de jogo e dificuldade|Modos-de-Jogo-e-Dificuldade-(pt-BR)]].
* **Habilidades.** Um cartão por espaço — **Ativa**, **Passiva 1** a **Passiva N**, e um cartão
  fixo **Inata** para cada passiva que a ave concede por conta própria — cada um com o ícone, o
  nível e o nome da habilidade; um cartão sem nada mostra **Vazio**. Enter alterna o cartão
  entre as habilidades que aquele espaço aceita. **Ver detalhes** abre o painel que lista cada
  habilidade com tipo, etiquetas, nível, preço do próximo nível e o que cada nível faz (as
  equipadas aparecem como **Equipada**, e uma que as regras da partida removeriam fica
  acinzentada como "Removida por …"), seguido do detalhamento dos atributos: a física resolvida
  da partida que começaria agora, uma linha por contribuição (ave, nó de melhoria, sinergia,
  mundo, dificuldade). Comprar Peso Pena nas árvores de melhorias acrescenta uma linha sob
  Gravidade ali e baixa o valor de 1800 para 1746. O painel rola com a roda, com as setas ou com
  Page Up/Page Down.
* **O botão e a navegação.** O botão dourado diz a única coisa a fazer com a ave acima dele:
  **Usar \<ave\>** para uma ave adquirida, **Ave selecionada** quando ela já é a sua,
  **Comprar · \<preço\>** quando você pode pagar, ou **Bloqueado · \<condição\>** quando não
  pode. Abaixo dele, os cinco itens da tela inicial — Loja, Aves, Jogar, Forja, Metas — com
  **Aves** na placa dourada: **Jogar** inicia a partida com tudo o que está na tela, e os outros
  três trocam de seção. Não há botão Voltar: Esc ou o gesto de voltar retorna à tela inicial, e
  fecha antes um painel aberto.

| Ave | Paleta | Desbloqueada por |
| --- | --- | --- |
| Asa-forjada | Brasa (Ember) | concluir o desafio Sem Escudo I |
| Asa-forjada | Vidro do Vazio (Voidglass) | vencer o chefe do Vazio |
| Zéfiro | Cometa (Comet) | concluir o desafio Corrida I |
| Bigorna | Basalto (Basalt) | passar 500 portões no total |
| Bico-de-ferro | Bronze | concluir o desafio Mundo em Movimento I |
| Gralha | Dourado (Gilded) | concluir o desafio Corrida do Ouro I |
| Oráculo | Aurora | a conquista Mestre das Habilidades (usar habilidades 200 vezes) |
| Brasa | Fundido (Molten) | possuir 50% dos níveis de melhoria |

## As oito habilidades

Uma habilidade **ativa** é disparada com a tecla de habilidade (`X`, `Shift` ou o botão direito
do mouse, veja [[Controles|Controles-(pt-BR)]]) e só funciona enquanto a duração dela corre; uma
**passiva** vale a partida inteira. Durações e recargas são contadas em ticks, e a simulação roda
a 60 ticks por segundo, então 300 ticks são cinco segundos. O nível 1 vem com o desbloqueio.

| `id` | Nome | Tipo | Etiquetas | Nível 1 | Conquiste com | Ou compre por |
| --- | --- | --- | --- | --- | --- | --- |
| `double_flap` | Batida Dupla (Double Flap) | Ativa | Movimento | Cancela a queda e bate de novo com 1,5× de força; 2 cargas, uma de volta a cada 5 portões; sem recarga | disponível desde o início | — |
| `shield` | Escudo (Shield) | Passiva | Defensiva | +1 carga de escudo: absorve um golpe e dá 45 ticks de graça | jogar 5 partidas | 200 moedas |
| `dash` | Arranco (Dash) | Ativa | Movimento | 20 ticks a duas vezes e meia a velocidade do mundo, sem gravidade e invulnerável enquanto dura; recarga de 600 ticks | passar 10 portões numa partida | 250 moedas |
| `coin_magnet` | Ímã de Moedas (Coin Magnet) | Passiva | Economia | +90 px de raio do ímã: as moedas próximas são puxadas para você | ganhar 500 moedas no total | 120 moedas |
| `slow_time` | Tempo Lento (Slow Time) | Ativa | Ritmo | O mundo corre a meia velocidade por 90 ticks; o pássaro, não; recarga de 900 ticks | chegar ao nível 4 | 350 moedas |
| `emergency_recovery` | Recuperação de Emergência (Emergency Recovery) | Passiva | Defensiva, Reviver | +1 revive: traz você de volta uma vez, com uma batida e 90 ticks de graça | passar 150 portões no total | 400 moedas |
| `score_multiplier` | Multiplicador de Pontos (Score Multiplier) | Ativa | Economia | Pontos em dobro por 300 ticks; recarga de 1200 ticks | passar 20 portões numa partida | 450 moedas |
| `invulnerability` | Invulnerabilidade (Invulnerability) | Ativa | Defensiva | Nada encosta em você por 120 ticks; recarga de 1500 ticks | concluir o desafio Uma Vida I | 700 moedas |

Os níveis 2 e 3 são comprados na aba Habilidades da Loja por duas e quatro vezes o preço de
compra:

| Habilidade | Nível 2 (preço) | Nível 3 (preço) |
| --- | --- | --- |
| Batida Dupla | 3 cargas (300) | 1,6× de batida, 3 cargas, uma de volta a cada 4 portões (600) |
| Escudo | a carga volta a cada 15 portões (400) | 60 ticks de graça, volta a cada 10 portões (800) |
| Arranco | 26 ticks, recarga 500, +6 ticks de graça (500) | 32 ticks, recarga 400, +12 ticks de graça (1000) |
| Ímã de Moedas | +40 px de raio (240) | +70 px de raio (480) |
| Tempo Lento | 120 ticks, recarga 800 (700) | 150 ticks, recarga 700 (1400) |
| Recuperação de Emergência | 105 ticks de graça, impulso ×1,15 (800) | 120 ticks de graça, impulso ×1,3 (1600) |
| Multiplicador de Pontos | 360 ticks, recarga 1050 (900) | 420 ticks, recarga 900 (1800) |
| Invulnerabilidade | 150 ticks, recarga 1300 (1400) | 180 ticks, recarga 1100 (2800) |

Cargas de escudo e revives são atributos comuns, então outras coisas os concedem sem a
habilidade: os nós Escudo Temperado e Segunda Chance da árvore Forja, e as cartas Escudo de
Campo, Segundo Fôlego e Fênix de uma escolha. No HUD, a habilidade ativa mostra **READY**, a
recarga restante em ticks ou as cargas; apertar a tecla sem nada equipado, durante a recarga ou
sem carga mostra o aviso correspondente.

## Regras do equipamento

* **Uma ativa e N passivas.** Uma partida leva uma habilidade ativa mais tantas passivas quantos
  forem os espaços da ave (2; Oráculo, 3) mais o +1 do nó Estudo de Habilidades, nunca mais de 4
  no total. O chip extra aparece assim que o nó é adquirido.
* **Passivas inatas** não ocupam espaço, não precisam de desbloqueio e não podem ser retiradas.
  O Escudo do Bico-de-ferro é a única do jogo.
* **Só o que você possui, e só no espaço certo.** Uma habilidade bloqueada, uma passiva no chip
  ativo ou uma ativa num chip passivo são recusadas e nada é gravado.
* **Os espaços escondem, não apagam.** Trocar do Oráculo para uma ave de dois espaços esconde a
  terceira passiva; voltar restaura a escolha.
* **Os níveis vão de 1 a 3, sob um limite.** O limite começa em 2; o nó Mestre-forja da árvore
  Forja sobe o limite para 3, e é o único nó que faz isso. A aba Habilidades da Loja mostra
  "Próximo nível N", "Limite de nível N", "Limite atingido" ou "Totalmente evoluída" conforme o
  caso. Um prestígio bloqueia as habilidades de novo (a Batida Dupla fica), zera os níveis e
  devolve o limite a 2.
* **As regras da partida removem; o perfil guarda.** A regra `NO_DEFENSIVE_ABILITIES` ("Sem
  habilidades defensivas") dos desafios Sem Escudo I e Uma Vida I zera os escudos e remove toda
  habilidade Defensiva (Escudo, Recuperação de Emergência, Invulnerabilidade, inclusive o escudo
  inato do Bico-de-ferro); a regra `NO_REVIVE` ("Sem reviver") de Uma Vida I remove os revives. A
  tela Aves acinzenta a habilidade com "Removida por …", a partida avisa "Esta corrida não
  permite …", e nada é desequipado: o equipamento volta na próxima partida padrão. Veja
  [[Desafios e metas|Desafios-e-Metas-(pt-BR)]].

Páginas relacionadas: [[Jogando uma partida|Jogando-uma-Partida-(pt-BR)]] para o que o HUD
mostra, [[Loja e melhorias|Loja-e-Melhorias-(pt-BR)]] para os nós que alteram as habilidades e
[[Perguntas frequentes|Perguntas-Frequentes-(pt-BR)]] para dúvidas comuns sobre combinações.
