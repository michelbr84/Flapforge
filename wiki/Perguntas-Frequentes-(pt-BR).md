_Idioma:_ [[English|FAQ]] · **Português (Brasil)**

# Perguntas frequentes

Respostas curtas para as dúvidas mais comuns, cada uma com um link para a página que tem os
detalhes.

## Onde fica meu save e como eu o reinicio?

Seu perfil — `save.json`, o backup `save.json.bak`, uma pasta `backups/` e `settings.json` — fica
em `~/.flapforge` no Linux, `%APPDATA%\Flapforge` no Windows e
`~/Library/Application Support/Flapforge` no macOS (ou onde `--home DIR` apontar). Para começar do
zero, abra o jogo uma vez com `--reset-save`: o save antigo e seu backup são postos de lado como
`save.reset-<hora>.json` e `save.bak.reset-<hora>.json`, nunca apagados, então você pode devolvê-los
à mão. Veja [[Primeiros passos|Primeiros-Passos-(pt-BR)]] e
[[Configurações e acessibilidade|Configuracoes-e-Acessibilidade-(pt-BR)]].

## Existe jogo online, placar de líderes ou contas?

Não. O Flapforge é um jogo para um jogador, sem recursos de rede, contas, telemetria ou serviços
remotos; recordes, sementes e diárias ficam no save local. Placares foram deixados de fora da
primeira versão de propósito (não há infraestrutura online); em vez disso o histórico das suas
partidas é guardado localmente. A diária também não precisa de servidor: a semente dela é apenas a
data UTC, então todo mundo recebe a mesma partida no mesmo dia. Veja
[[Modos de jogo e dificuldade|Modos-de-Jogo-e-Dificuldade-(pt-BR)]].

## Abri o jogo com `--world wind_valley` e ele ignorou. Por quê?

`--world` seleciona um mundo, não desbloqueia. Quando seu perfil possui o mundo, a opção grava a
seleção exatamente como a tela Escolha um mundo faria, e a placa da tela inicial e o INICIAR
PARTIDA o jogam. Quando não possui, o perfil fica intocado e uma linha na saída padrão avisa
(`--world <id>: not unlocked in this profile …`); o INICIAR PARTIDA continua jogando sua seleção
atual. Desbloqueie o mundo primeiro — veja a pergunta sobre mundos abaixo e
[[Mundos e chefes|Mundos-e-Chefes-(pt-BR)]].

## Desbloqueei algo na hora do almoço, mas a diária continua igual. Por quê?

De propósito. O sorteio da diária — mundo, dificuldade e dois modificadores forçados, todos tirados
do que você possui — é gravado no seu perfil na primeira vez em que o modo Diária é *visto* ou
jogado em uma data UTC, e toda pergunta posterior sobre aquela data é respondida pelo registro.
Assim, um mundo desbloqueado ao meio-dia não move a partida que você treinou de manhã; só o
contador de tentativas e o recorde de portões mudam, e uma nova tentativa mantém a semente. A
diária de amanhã já sorteia com o conteúdo novo. Veja
[[Modos de jogo e dificuldade|Modos-de-Jogo-e-Dificuldade-(pt-BR)]].

## Uma nova tentativa imediata perde as recompensas da partida que acabou?

Não. Moedas, XP, conquistas e desbloqueios são creditados no instante em que a partida termina,
antes da faixa de fim de jogo aparecer, então apertar `Space` (ou clicar) para tentar de novo
nunca os perde. Uma nova tentativa no modo Padrão começa com uma semente nova; na Diária ela
mantém a semente e só conta a tentativa. Veja
[[Jogando uma partida|Jogando-uma-Partida-(pt-BR)]].

## Como jogo em português?

Abra Configurações (a engrenagem da tela inicial) e mude a primeira linha, **Idioma**, para
**Português (Brasil)**; a troca é imediata. **Automático** segue o idioma do sistema. Só por uma
execução, passe `--lang pt_BR` (ou `--lang en`) na linha de comando. Veja
[[Configurações e acessibilidade|Configuracoes-e-Acessibilidade-(pt-BR)]].

## Não tem som. O que eu faço?

Primeiro confira a seção Som das Configurações (**Silenciar tudo**, os três volumes) e a tecla
`M`, que silencia em qualquer tela e é lembrada. Se o jogo imprimiu `Audio: no output device` na
inicialização, nenhuma saída de som utilizável foi encontrada (um contêiner, um PulseAudio
ocupado, nenhuma placa de som) e ele está rodando em silêncio de propósito; `--no-audio` escolhe
esse caminho deliberadamente, o que é útil em máquinas cujo sistema de áudio demora para abrir. O
jogo roda exatamente igual nos dois casos. Veja
[[Configurações e acessibilidade|Configuracoes-e-Acessibilidade-(pt-BR)]].

## A janela está grande ou pequena demais.

O campo de jogo tem 420×640 pixels lógicos e a janela abre na maior escala inteira que cabe na
altura da sua tela (2× em um monitor da classe 1440, 1× em 1080p). Passe `--scale N` para escolher
uma escala, ou aperte `F11` para tela cheia sem bordas. Em Configurações › Vídeo, **Escala
inteira** mantém a imagem em escalas de pixel inteiro, **Preencher a tela** pinta céu e chão sobre
as faixas de janelas altas e **Suavização** controla a filtragem em escalas fracionárias. Veja
[[Configurações e acessibilidade|Configuracoes-e-Acessibilidade-(pt-BR)]].

## Como saio do jogo?

Aperte `Esc` na tela inicial: aparece o aviso "Pressione de novo para sair", e um segundo toque
dentro de três segundos fecha o jogo. Configurações › Sobre também tem a linha **Sair** no
computador. No Android nenhum dos dois existe; saia do aplicativo pelo gesto Voltar do sistema.
Veja [[Tela inicial|Tela-Inicial-(pt-BR)]].

## O que o item Forja da tela inicial abre?

O item **Forja** da navegação inferior abre a oficina de melhorias inteira — a tela intitulada
**Melhorias**, com as árvores de voo, economia e forja em que moedas compram níveis permanentes de
atributos. Não é o mundo Forja de Ferro (Iron Forge), a ave Brasa (Cinder) nem uma árvore só. Veja
[[Loja e melhorias|Loja-e-Melhorias-(pt-BR)]].

## Como desbloqueio o Difícil e o Pesadelo?

Cada dificuldade abre por um de dois caminhos. **Difícil** (rolagem ×1,10, vão ×0,92, recompensas
×1,5): passe 40 portões numa partida *ou* 400 portões no total do perfil; o nó de melhoria
`hard_tier_1` (400 moedas) é um atalho pago. **Pesadelo** (rolagem ×1,20, vão ×0,85, todos os
obstáculos se movem, teto letal, recompensas ×2,5): complete o desafio Chefe do Corredor *ou*
chegue ao nível 20. A dificuldade é escolhida na linha de dificuldade da tela Escolha um mundo.
Veja [[Modos de jogo e dificuldade|Modos-de-Jogo-e-Dificuldade-(pt-BR)]] e
[[Desafios e metas|Desafios-e-Metas-(pt-BR)]].

## Como desbloqueio os outros mundos?

Todo mundo depois de Campos Verdes (Green Fields) abre ou vencendo o chefe do mundo anterior ou
comprando-o na Loja: Vale do Vento (Wind Valley) 350 moedas, Forja de Ferro 700, Céu de Tempestade
(Storm Sky) 1200, O Vazio (The Void) 2000. O chefe de Campos Verdes espera no portão 30. Os
desafios ignoram tudo isso: um desafio acontece no mundo dele, você o possuindo ou não. Veja
[[Mundos e chefes|Mundos-e-Chefes-(pt-BR)]] e [[Loja e melhorias|Loja-e-Melhorias-(pt-BR)]].

## Dá para jogar no Android?

Sim. Toda versão na [página de releases](https://github.com/michelbr84/Flapforge/releases) traz um
`Flapforge-<versão>-android.apk` ao lado dos pacotes para computador. É um APK para instalação
manual, assinado com a chave de depuração, para Android 13 ou mais novo — ele não está em nenhuma
loja, então permita a instalação a partir da fonte com que você o baixou. O toque funciona em todo
lugar: toque para bater asas, os botões dos painéis de fim de jogo e de pausa, e telas em retrato
são preenchidas de ponta a ponta. Veja [[Primeiros passos|Primeiros-Passos-(pt-BR)]] e
[[Controles|Controles-(pt-BR)]].

## Minhas configurações voltaram ao padrão depois de uma atualização. Por quê?

O arquivo `settings.json` carrega uma versão. Quando um build encontra um arquivo de versão
diferente, ele não o carrega: os padrões são restaurados, um aviso nomeia o arquivo antigo, e o
arquivo é mantido ao lado do novo como `settings.v<N>.json`, então nada se perde. Veja
[[Configurações e acessibilidade|Configuracoes-e-Acessibilidade-(pt-BR)]].

## Como relato um bug ou proponho um recurso?

Abra uma issue em <https://github.com/michelbr84/Flapforge/issues> usando os modelos: *Bug report*
pede um resumo, o comando exato de abertura, os passos e o comportamento esperado e o observado;
*Feature request* pede o problema e a mudança proposta. Inclua seu sistema, o JDK
(`java -version`), como você abriu o jogo e, no caso de uma partida, a semente mostrada no painel
`F3`. Os modelos e as issues são em inglês. Relatos sensíveis de segurança seguem o
[`SECURITY.md`](https://github.com/michelbr84/Flapforge/blob/main/SECURITY.md). Para contribuir
você mesmo com a correção, veja
[[Compilando e contribuindo|Compilando-e-Contribuindo-(pt-BR)]].
