_Idioma:_ [[English|Home]] · **Português (Brasil)**

# Flapforge — Início

**Um roguelite de arcade baseado em habilidade, em que cada voo deixa o próximo mais forte.**

O Flapforge mantém o ciclo de um botão do clássico — *bata as asas, desvie, sobreviva, pontue* —
e dá a cada partida um propósito maior. Morrer ainda encerra o voo, mas nunca a jornada: cada
partida paga moedas e experiência, e o que você ganha desbloqueia aves, habilidades, mundos,
modificadores, desafios e melhorias permanentes que tornam a próxima partida diferente da
anterior.

![A tela inicial](images/hub.png)

*A tela inicial: sua ave na forja, o mundo que você vai voar em seguida, o próximo desbloqueio e
um grande INICIAR PARTIDA (interface em inglês).*

## O ciclo

```text
Iniciar a partida → voar → desviar → pontuar → escolher um modificador → enfrentar o chefe → morrer ou vencer
        ↑                                                                                       ↓
   montar a próxima build ← desbloquear ou melhorar ← receber moedas e XP ←────────────────────┘
```

A habilidade decide a partida; a progressão decide com o que a habilidade vai trabalhar. Tudo é
local e determinístico: sementes, diárias e recordes ficam em um arquivo de salvamento à prova de
travamentos no seu computador, e não há serviços online, contas nem placares.

## Comece por aqui

1. [[Primeiros passos|Primeiros-Passos-(pt-BR)]] — requisitos, downloads, execução a partir do
   código, opções de linha de comando e onde fica o seu salvamento.
2. [[Tela inicial|Tela-Inicial-(pt-BR)]] — um passeio pela primeira tela: o cartão do jogador, a
   placa do mundo, a forja, o cartão de próximo desbloqueio, INICIAR PARTIDA e a barra inferior.
3. [[Controles|Controles-(pt-BR)]] — teclas, mouse, toque e reatribuição.
4. [[Jogando uma partida|Jogando-uma-Partida-(pt-BR)]] — o que acontece entre PRONTO e o resumo
   da partida, e como as recompensas são pagas.

## Os sistemas

| Página | O que cobre |
| --- | --- |
| [[Modificadores e sinergias|Modificadores-e-Sinergias-(pt-BR)]] | A escolha no meio da partida: 17 modificadores, raridades, acúmulos e as quatro sinergias. |
| [[Aves e habilidades|Aves-e-Habilidades-(pt-BR)]] | As sete aves, suas cores, as oito habilidades e o equipamento. |
| [[Mundos e chefes|Mundos-e-Chefes-(pt-BR)]] | Os cinco mundos, seus perigos e chefes, e a tela Escolha um mundo. |
| [[Loja e melhorias|Loja-e-Melhorias-(pt-BR)]] | Moedas, as abas da Loja e as três árvores de melhorias atrás do item Forja. |
| [[Modos de jogo e dificuldade|Modos-de-Jogo-e-Dificuldade-(pt-BR)]] | Partidas Padrão, Com semente, Diária e Desafio, os níveis de dificuldade e o prestígio. |
| [[Desafios e metas|Desafios-e-Metas-(pt-BR)]] | A tela Metas: sete desafios, 41 conquistas, marcos e coleções. |
| [[Configurações e acessibilidade|Configuracoes-e-Acessibilidade-(pt-BR)]] | Idioma, som, vídeo, opções de jogo, teclas e a seção Sobre. |
| [[Compilando e contribuindo|Compilando-e-Contribuindo-(pt-BR)]] | Compilar a partir do código, as tarefas do Gradle, adicionar conteúdo e contribuir. |
| [[Perguntas frequentes|Perguntas-Frequentes-(pt-BR)]] | Respostas curtas para as dúvidas mais comuns. |

## Resumo

| | |
| --- | --- |
| Plataformas | Linux, Windows 10+, macOS 12+ (Java 17+, AWT/Java2D puro); Android 13+ (APK para instalação manual) |
| Área de jogo | 420×640 pixels lógicos a 60 Hz, escalados para qualquer janela; telas altas são preenchidas de ponta a ponta |
| Conteúdo | 7 aves, 8 habilidades, 17 modificadores, 3 árvores de melhorias, 5 mundos, 7 desafios, 41 conquistas |
| Idiomas | Inglês e Português (Brasil), trocáveis ao vivo em Configurações ou com `--lang pt_BR` |
| Licença | MIT — veja o [repositório](https://github.com/michelbr84/Flapforge) |
| Downloads | [Releases](https://github.com/michelbr84/Flapforge/releases): imagens de aplicativo por sistema, um jar completo e o APK Android |

> **Dica:** este wiki é gerado a partir do diretório `wiki/` do repositório. Encontrou um erro?
> Abra um pull request alterando `wiki/` em vez de editar a página aqui — a próxima
> sincronização sobrescreveria a edição.
