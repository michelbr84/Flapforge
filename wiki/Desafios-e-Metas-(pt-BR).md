_Idioma:_ [[English|Challenges-and-Goals]] · **Português (Brasil)**

# Desafios e metas

Além do recorde, o Flapforge dá a cada partida um objetivo estruturado: sete desafios, 41
conquistas, barras de marcos e porcentagens de coleção. Tudo isso fica em uma única tela, o item
**Metas** da navegação inferior da [[Tela inicial|Tela-Inicial-(pt-BR)]], e tudo paga moedas pela
mesma carteira de uma partida normal (veja [[Loja e melhorias|Loja-e-Melhorias-(pt-BR)]]).

![A tela Metas, aba Desafios](images/goals-challenges.png)

*A tela Metas na primeira aba: os sete desafios, o bloco de detalhes do desafio selecionado e o
botão Jogar (interface em inglês).*

## A tela Metas

A tela tem quatro abas, percorridas com as setas ou clicadas diretamente:

| Aba | O que mostra |
| --- | --- |
| **Desafios** | os sete desafios na ordem do conteúdo, com um bloco de detalhes e o botão Jogar |
| **Conquistas** | as 41 conquistas: as desbloqueadas com a data, as secretas como `???` |
| **Marcos** | a barra de nível e, em seguida, os cinco limiares mais próximos, cada um com sua barra |
| **Coleções** | uma barra por categoria de conteúdo: quanto dela você já possui, e um total |

Só a aba Desafios inicia uma partida; as outras três são apenas de leitura. O cabeçalho da aba
Conquistas faz a contagem ("12 de 41 desbloqueadas").

## Desafios

Um desafio é uma partida fechada em si mesma: o desafio fixa o mundo, a dificuldade, as regras,
as cartas de modificador forçadas e, em um deles, um chefe próprio. Todo o resto — sua ave, a
paleta dela e sua configuração — vem do perfil. Um desafio pode ser jogado **com ou sem o mundo
desbloqueado**: o mundo é um lugar, nunca um requisito, e três dos sete acontecem em mundos que
um perfil novo não possui. Nenhum sorteio de modificador é oferecido durante um desafio.

O bloco de detalhes mostra o mundo, a dificuldade, as regras ("Regras padrão" quando não há
nenhuma), o objetivo por extenso, as recompensas e seu registro — "Ainda não jogado", "Melhor 17
portões, 3 tentativas" ou "Concluído". Um desafio bloqueado mostra a condição de desbloqueio no
lugar do registro.

| Id | Nome | Mundo | Dificuldade | Regras | Objetivo | Desbloqueado por | Recompensa da primeira conclusão |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `no_shield_1` | Sem Escudo I | Campos Verdes (Green Fields) | Normal | `NO_DEFENSIVE_ABILITIES` | Sobreviva a 30 portões | Passe 20 portões numa partida, ou jogue 12 partidas | 200 moedas + paleta Brasa (Ember) da Asa-forjada (Forgewing) |
| `speed_run_1` | Corrida I | Vale do Vento (Wind Valley) | Normal | `SPEED_RAMP`: o mundo acelera sem parar | Sobreviva a 30 portões | Passe 25 portões numa partida, ou jogue 15 partidas | 250 moedas + paleta Cometa (Comet) do Zéfiro (Zephyr) |
| `tiny_wings_1` | Asas Pequenas I | Campos Verdes | Normal | `FLAP_VELOCITY` ×0,7, curva `classic` | Sobreviva a 20 portões | Passe 15 portões numa partida, ou jogue 10 partidas | 150 moedas |
| `moving_world_1` | Mundo em Movimento I | Campos Verdes | Normal | `ALL_OBSTACLES_MOVE` | Sobreviva a 25 portões | Vença Campos Verdes, ou passe 300 portões no total | 250 moedas + paleta Bronze do Bico-de-ferro (Ironbeak) |
| `one_life_1` | Uma Vida I | Forja de Ferro (Iron Forge) | Normal | `NO_DEFENSIVE_ABILITIES`, `NO_REVIVE` | Sobreviva a 30 portões | Complete Sem Escudo I, ou jogue 25 partidas | 400 moedas + a habilidade Invulnerabilidade (Invulnerability) |
| `coin_rush_1` | Corrida do Ouro I | Vale do Vento | Normal | `COIN_SPAWN_RATE` ×3, começa com Chuva de Moedas (Coin Drops) | Colete 60 moedas | Ganhe 1000 moedas no total, ou chegue ao nível 6 | 300 moedas + paleta Dourado (Gilded) da Gralha (Jackdaw) |
| `boss_corridor_1` | Chefe do Corredor | Campos Verdes | Normal | corredor fixo (`corridor_1`), chefe próprio no portão 20 | Vença o chefe | Vença Campos Verdes, ou chegue ao nível 12 | 500 moedas + a dificuldade Pesadelo |

Observações sobre a tabela:

- "Vença Campos Verdes" significa sobreviver uma vez ao chefe do mundo Campos Verdes (veja
  [[Mundos e chefes|Mundos-e-Chefes-(pt-BR)]]). Toda condição é um *ou*: o primeiro ramo que você
  cumprir abre o desafio.
- O objetivo é avaliado a cada tick e travado no instante em que é cumprido — aparece o aviso
  "Objetivo concluído" e a partida simplesmente continua, então você pode seguir voando por
  moedas.
- A **primeira** conclusão paga as moedas e o desbloqueio listados acima; toda conclusão
  seguinte paga só o bônus de desafio de 100 moedas da fórmula de recompensas (veja
  [[Jogando uma partida|Jogando-uma-Partida-(pt-BR)]]).
- O Chefe do Corredor é o chefe *próprio* do desafio: ele avisa 120 ticks antes do portão 20 e
  precisa ser sobrevivido por 900 ticks (15 segundos). Não é um chefe de mundo, portanto não paga
  a recompensa de chefe de mundo nem conta para as conquistas de chefe abaixo.
- As quatro paletas são cosméticos das aves indicadas; Invulnerabilidade entra nas habilidades
  que você pode equipar; a dificuldade Pesadelo aparece na linha de dificuldade (veja
  [[Modos de jogo e dificuldade|Modos-de-Jogo-e-Dificuldade-(pt-BR)]]).

> **Dica:** os desafios são o caminho mais barato para o Pesadelo e para a Invulnerabilidade.
> Tanto `boss_corridor_1` quanto `one_life_1` têm um ramo alternativo que só depende de tempo
> (nível 12, 25 partidas), então um jogador paciente chega a eles sem comprar nada.

## Conquistas

As conquistas são avaliadas automaticamente sempre que uma partida termina e sempre que uma
compra é feita. Cada uma é uma única condição em um de três escopos — um contador de todo o
tempo, um registro da partida que acabou de terminar ou uma porcentagem de coleção — e cada uma
dispara exatamente uma vez, paga suas moedas e mostra um aviso. Duas são secretas: são avaliadas
como qualquer outra, mas a lista mostra `???` e "Uma conquista secreta" até elas dispararem, e
elas nunca ganham uma barra de marco.

| Id | Nome | Condição | Moedas |
| --- | --- | --- | --- |
| `first_flight` | Primeiro Voo | Termine uma partida | 25 |
| `frequent_flyer` | Voador Frequente | Termine 25 partidas | 100 |
| `veteran` | Veterano | Termine 100 partidas | 300 |
| `centurion` | Centurião | Termine 250 partidas | 800 |
| `gates_10` | Dez Canos | Passe 10 canos em uma partida | 50 |
| `gates_25` | Vinte e Cinco Canos | Passe 25 canos em uma partida | 100 |
| `gates_50` | Cinquenta Canos | Passe 50 canos em uma partida | 250 |
| `gates_100` | Cem Canos | Passe 100 canos em uma partida | 600 |
| `marathon` | Maratona | Passe 1000 canos no total | 400 |
| `odyssey` | Odisseia | Passe 5000 canos no total | 1200 |
| `points_100` | Cem Pontos | Faça 100 pontos em uma partida | 50 |
| `points_500` | Quinhentos Pontos | Faça 500 pontos em uma partida | 200 |
| `points_1000` | Mil Pontos | Faça 1000 pontos em uma partida | 500 |
| `coin_collector` | Colecionador de Moedas | Colete 100 moedas | 50 |
| `treasurer` | Tesoureiro | Ganhe 2000 moedas | 200 |
| `tycoon` | Magnata | Ganhe 10000 moedas | 800 |
| `big_spender` | Grande Gastador | Gaste 5000 moedas | 500 |
| `clean_10` | Dez Limpos | Chegue a uma sequência limpa de 10 em uma partida | 100 |
| `clean_25` | Vinte e Cinco Limpos | Chegue a uma sequência limpa de 25 em uma partida | 300 |
| `first_save` | Salvo | Tenha um escudo absorvendo um golpe | 50 |
| `ability_adept` | Adepto das Habilidades | Use habilidades 50 vezes | 100 |
| `ability_master` | Mestre das Habilidades | Use habilidades 200 vezes | 400 |
| `boss_green_fields` | Marechal dos Campos | Vença o chefe dos Campos Verdes | 150 |
| `boss_wind_valley` | Quebra-vento | Vença o chefe do Vale do Vento | 200 |
| `boss_iron_forge` | Quebra-forja | Vença o chefe da Forja de Ferro | 250 |
| `boss_storm_sky` | Chama-tempestade | Vença o chefe do Céu de Tempestade (Storm Sky) | 300 |
| `boss_void` | Andarilho do Vazio (secreta) | Vença o chefe do Vazio (The Void) | 400 |
| `boss_hunter` | Caçador de Chefes | Vença o chefe de todos os mundos (os cinco) | 1000 |
| `first_challenge` | Desafiante | Complete um desafio | 100 |
| `challenge_master` | Mestre dos Desafios | Complete todos os desafios (os sete) | 750 |
| `collect_all_birds` | Aviário Completo | Desbloqueie todos os pássaros | 500 |
| `collect_all_abilities` | Repertório Completo | Desbloqueie todas as habilidades | 500 |
| `collect_all_worlds` | Cartógrafo | Desbloqueie todos os mundos | 500 |
| `collect_all_cosmetics` | Guarda-roupa Completo | Desbloqueie todas as paletas | 750 |
| `completionist` | Completista (secreta) | Complete todas as coleções | 2000 |
| `hard_10` | Dez no Difícil | Passe 10 canos no nível Difícil | 200 |
| `nightmare_10` | Dez no Pesadelo | Passe 10 canos no nível Pesadelo | 500 |
| `level_10` | Nível Dez | Chegue ao nível 10 | 300 |
| `level_25` | Nível Vinte e Cinco | Chegue ao nível 25 | 1000 |
| `daily_first` | Voador Diário | Jogue um desafio diário | 50 |
| `daily_week` | Sete Dias | Jogue sete desafios diários | 300 |

As condições "em uma partida" são registros da partida que acabou de terminar, então só podem
disparar no fim de uma partida. "Vencer" um chefe é sobreviver à fase de chefe de um mundo (veja
[[Mundos e chefes|Mundos-e-Chefes-(pt-BR)]]); "desafio diário" é o modo Diária de
[[Modos de jogo e dificuldade|Modos-de-Jogo-e-Dificuldade-(pt-BR)]]. Um prestígio mantém todas as
conquistas que você tem e redefine os registros de desafio.

## Marcos

A aba Marcos abre com sua barra de nível e depois lista os "Próximos marcos": os cinco limiares
mais próximos entre as recompensas de nível ainda não recebidas e as conquistas de limiar de todo
o tempo que ainda não dispararam, do mais próximo ao mais distante, cada um com uma barra de
progresso "atual / alvo". As conquistas por partida usam sua melhor estatística correspondente
(melhor sequência, recorde de portões, recorde de pontos), então a barra continua avançando entre
partidas. Quando não resta nada, a aba diz "Todos os marcos alcançados".

As recompensas de nível são pagas uma vez, na primeira vez que você chega ao nível (o nível
máximo é 50):

| Nível | 2 | 5 | 10 | 15 | 20 | 25 |
| --- | --- | --- | --- | --- | --- | --- |
| Moedas | 50 | 150 | 500 | 800 | 1200 | 2000 |

## Coleções

A aba Coleções desenha uma barra por categoria de conteúdo, "possuído / total (porcentagem)",
com a porcentagem arredondada para baixo: **Aves** (7), **Habilidades** (8), **Mundos** (5),
**Desafios** (7), **Cores** (as paletas das aves), **Conquistas** (41), **Melhorias** (os nós
das árvores) e, por último, **Tudo**. As quatro conquistas "Completo" acima disparam com 100 %
de aves, habilidades, mundos e cores; a secreta Completista dispara quando Tudo chega a 100 %. O
cabeçalho do Perfil, na [[Tela inicial|Tela-Inicial-(pt-BR)]], mostra a forma curta: "Aves x/7 ·
Mundos x/5 · Conquistas x/41".

## Avisos e o cartão Próximo desbloqueio

Tudo o que acontece nesta tela se anuncia na hora. Os avisos que você vai ver:

| Aviso | Quando |
| --- | --- |
| "Conquista: *nome* (+*n* moedas)" | uma conquista disparou e pagou suas moedas |
| "Desbloqueado: *nome*" | um desbloqueio foi concedido — por recompensa de desafio, conquista, chefe vencido ou limiar |
| "Desafio concluído: *nome*" | a primeira conclusão de um desafio foi registrada |
| "Objetivo concluído" | no meio da partida, no instante em que o objetivo do desafio é cumprido |
| "Nível *n*" | um nível foi alcançado (a recompensa dele, se houver, chega em moedas) |

O cartão **Próximo desbloqueio** da tela inicial sempre nomeia o desbloqueável mensurável mais
próximo: o id não cosmético que você ainda não possui cujo ramo mais perto está mais próximo de
ser concluído, junto com seu contador ("Passe 30 portões numa partida", "17 / 30"). Ramos que se
conquistam jogando vêm antes dos que só se compram. Pressionar o cartão abre a tela onde aquilo
é conquistado: Aves, Escolha um mundo, Forja, Metas ou Loja. Quando não resta nada mensurável, o
cartão diz "Tudo desbloqueado".
