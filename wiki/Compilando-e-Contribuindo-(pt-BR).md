_Idioma:_ [[English|Building-and-Contributing]] · **Português (Brasil)**

# Compilando e contribuindo

O Flapforge é um único módulo Gradle de Java 17 puro — sem motor de jogo, sem código nativo, uma
única dependência em tempo de execução (Gson) — sob a licença MIT. Esta página é a versão curta
de [`docs/DEVELOPMENT.md`](https://github.com/michelbr84/Flapforge/blob/main/docs/DEVELOPMENT.md)
e [`CONTRIBUTING.md`](https://github.com/michelbr84/Flapforge/blob/main/CONTRIBUTING.md): o
suficiente para compilar o jogo, rodar os testes, adicionar conteúdo e abrir um pull request. Se
você só quer jogar, [[Primeiros passos|Primeiros-Passos-(pt-BR)]] cobre os downloads.

## Pré-requisitos

| Requisito | Observações |
| --- | --- |
| JDK 17 ou mais novo | qualquer distribuição (Temurin, Microsoft, Zulu, o pacote da distro); o build compila contra `--release 17`, então um JDK mais novo serve. `java -version` precisa funcionar no terminal |
| Git | para clonar e contribuir |
| Uma sessão gráfica | Linux (X11 ou Wayland/XWayland), Windows 10+, macOS 12+; o jogo é AWT/Java2D puro |
| Nada mais | nenhuma instalação do Gradle: o wrapper versionado baixa o Gradle 9.7.1 no primeiro uso e o guarda em `~/.gradle` |

## Preparação

```bash
git clone https://github.com/michelbr84/Flapforge.git
cd Flapforge
./gradlew build        # a primeira execução baixa Gradle 9.7.1 + Gson + JUnit
./gradlew run          # abre a janela do jogo
```

`./gradlew --offline build` funciona assim que as dependências estão no cache local. O Gradle pode
levar alguns minutos com o cache frio; tenha paciência. No Windows use `gradlew.bat` (ou os
scripts `scripts\*.ps1`).

## Tarefas do Gradle

| Tarefa | Finalidade |
| --- | --- |
| `./gradlew build` | compila (`-Xlint:all,-serial -Werror -parameters`, UTF-8, `--release 17`) e roda a suíte de testes padrão; **o portão que todo pull request precisa passar** |
| `./gradlew test` | a suíte padrão: só testes puros e sem janela (`java.awt.headless=true`; as tags `gui`, `perf` e `sim` ficam de fora) |
| `./gradlew smokeTest` | testes com a tag `gui`: uma janela real, tela cheia alternada duas vezes, navegação do menu via Robot, o caminho real de saída, capturas em `build/smoke/`. **Precisa de uma tela**; sem ela os testes são pulados, não reprovados |
| `./gradlew simTest` | testes com a tag `sim`: simulações longas com bots (viabilidade do conteúdo, jornada do jogador novo, metaprogressão) |
| `./gradlew perfTest` | testes com a tag `perf`: orçamentos locais de desempenho (não rodam na CI) |
| `./gradlew contentCheck` | roda o validador de conteúdo e a checagem de strings sobre o JSON distribuído e imprime o grafo de desbloqueios |
| `./gradlew fatJar` | o jar autocontido `build/libs/flapforge-<versão>-all.jar` (com o Gson embutido) |
| `./gradlew iconExport` | exporta o ícone procedural em `.png`, `.ico` e `.icns` para o `jpackage` |
| `./gradlew run` | inicia o jogo a partir do código; as opções de linha de comando passam por `--args`, por exemplo `./gradlew run --args="--seed 42 --scale 2"` |

Scripts auxiliares: `scripts/build.sh` / `scripts\build.ps1` rodam `build fatJar`; `scripts/run.sh`
/ `scripts\run.ps1` rodam `run` e repassam todos os argumentos ao jogo; `scripts/package.sh` roda
`fatJar iconExport` e depois `jpackage --type app-image` em `build/dist/` (precisa do `jpackage`
que vem com o JDK 14+). A versão Android é um build Gradle separado em `android/`
(`./gradlew -p android assembleDebug`, em uma máquina com o SDK do Android); a CI o compila a cada
push e o fluxo de release anexa o APK a cada release `v*`.

No Linux os testes de fumaça precisam de um servidor X: defina `DISPLAY=:0` ou rode
`xvfb-run -a ./gradlew smokeTest`, como a CI faz. No Wayland a captura de tela pode vir preta; o
teste então recorre a uma renderização fora da tela e continua passando.

## Estrutura do repositório

| Caminho | Conteúdo |
| --- | --- |
| `src/main/java` | o jogo, pacote `io.github.michelbr84.flapforge`, em quatro camadas: `app` (janela, loop, ponte de entrada), apresentação (`render`, `audio`, `ui`, `event`), a simulação pura (`core`, `input`, `gameplay`, `ability`, `modifier`) e a camada meta pura (`content`, `progression`, `persistence`) |
| `src/main/resources` | `data/` (aves, dificuldade, economia, melhorias, habilidades, modificadores, mundos, padrões, desafios e conquistas em JSON), `data/strings/` (`en.json`, a fonte da verdade, e `pt_BR.json`), `assets/` (o manifesto e a fonte embutida), `version.properties` |
| `src/test` | testes unitários, de propriedade, de simulação, de renderização sem janela e de fumaça com interface, mais os fixtures |
| `src/tools` | o simulador de balanceamento, o inspetor de save, a checagem de conteúdo, o validador de assets e a exportação do ícone |
| `scripts/`, `docs/`, `.github/` | scripts de build/execução/empacotamento, a documentação de engenharia, os fluxos de CI e release, os modelos de issue e PR |
| `android/` | o build Gradle próprio da versão Android |
| `wiki/` | esta wiki (veja abaixo) |

As dependências só apontam para baixo, e as duas camadas inferiores nunca tocam AWT, relógio,
threads ou aleatoriedade global — `ArchitectureTest` reprova o build a qualquer violação. A árvore
completa de pacotes está em
[`docs/ARCHITECTURE.md`](https://github.com/michelbr84/Flapforge/blob/main/docs/ARCHITECTURE.md).

## Contribuindo

1. Faça um fork do repositório (ou crie um branch, se tiver permissão de escrita).
2. Crie o branch a partir de `main` com um prefixo de tipo e uma descrição curta, em minúsculas e
   com hífens: `feat/`, `fix/`, `docs/`, `refactor/`, `test/`, `chore/` ou `content/` (conteúdo
   JSON e balanceamento) — por exemplo `feat/wind-valley-gusts` ou `fix/pause-on-focus-loss`.
3. Faça commits focados seguindo o [Conventional Commits](https://www.conventionalcommits.org/):
   `<type>(<scope>): <short imperative summary>`, com `type` entre `feat`, `fix`, `docs`,
   `refactor`, `perf`, `test`, `build`, `ci`, `chore` e `content`; o resumo é em inglês, no
   imperativo, sem ponto final e com até 72 caracteres.
4. Rode `./gradlew build` localmente; ele precisa passar sem nenhum aviso do compilador (`-Werror`).
5. Rode `./gradlew smokeTest` se você mexeu em qualquer coisa sob `app`, `render`, `audio` ou `ui`.
6. Abra um pull request contra `main` e preencha o modelo.

A lista de verificação do pull request:

- [ ] `./gradlew build` passa localmente sem avisos.
- [ ] `./gradlew smokeTest` passa, se código de apresentação mudou.
- [ ] O comportamento novo ou alterado está coberto por testes.
- [ ] A documentação (`README.md`, `docs/*.md`, `CHANGELOG.md` em `[Unreleased]`) está atualizada.
- [ ] Os commits seguem o Conventional Commits e o branch segue o esquema de nomes.
- [ ] Nenhum asset ou dependência de terceiros novo sem nota de licença.

Mantenha os pull requests pequenos e com um único propósito, e abra uma issue antes de mudanças
grandes de jogabilidade ou arquitetura. As regras de código em resumo: Java 17 sem recursos de
prévia, avisos são erros, nada de Swing, os pacotes puros nunca importam AWT nem leem o relógio,
toda aleatoriedade passa por fluxos com semente, conteúdo como dados e não como código, inglês em
todo lugar (texto para o jogador passa pelas tabelas de strings), testes acompanham o
comportamento, e nenhum asset herdado ou sem licença.

## Conteúdo é JSON

Aves, melhorias, habilidades, modificadores, mundos, padrões, desafios, conquistas e as strings da
interface são arquivos JSON em `src/main/resources/data`, validados na inicialização. Chaves
desconhecidas são erros. Muitas adições não precisam de Java nenhum; as receitas estão em
[`docs/CONTENT.md`](https://github.com/michelbr84/Flapforge/blob/main/docs/CONTENT.md) §3
("How to add things"):

- **Uma ave** — acrescente uma entrada em `birds.json` (toda ave distribuída usa a hitbox
  `{w: 33, h: 31, ox: -17, oy: -12}`; `baseStats` lista só o que difere dos padrões), dê a ela
  uma paleta `default` e uma paleta `prestige`, um `unlock` com um caminho cumulativo
  (`any_of[<algo que exija habilidade>, {"type": "purchase", "amount": N}]`) e acrescente
  `bird.<id>.name` / `.desc` e `cosmetic.<id>.<paleta>.name` / `.desc` **nos dois** arquivos de
  strings.
- **Um mundo** — uma entrada em `worlds.json`: `id` e `order`, uma `curve` de dificuldade, um
  `style` de parallax (`hills`, `canyon`, `factory`, `storm`, `void`), o `unlock` (todo mundo
  além do primeiro é `any_of[world_cleared <anterior>, purchase N]`), uma `palette` de sete
  cores, `effects` e `flags`, a tabela `spawnWeights`, `patterns`, `ambient` (escuridão, vento,
  relâmpagos), `ruleCycles` opcional, o bloco `boss` com sua recompensa, e `music` / `sfxSet`.
  Veja "A world (M7)".
- **Um desafio** — uma entrada em `challenges.json`: `world`, `tier` e `curve` (o mundo é um
  lugar, nunca um requisito), `allowOffers`, `flags` e `effects`, `forcedModifiers`, um
  `forcedPattern` opcional e um `boss` próprio, o `objective` (`SURVIVE_GATES`, `SURVIVE_TICKS`,
  `COLLECT_COINS`, `REACH_POINTS` ou `BOSS_CLEARED`), os `rewards` da primeira conclusão e o
  `unlock`. Depois meça com o bot:
  `./gradlew balancing -PtoolArgs="--challenge my_challenge_1 --skill expert --seeds 50"`.
  Veja "A challenge (M8)" e [[Desafios e metas|Desafios-e-Metas-(pt-BR)]].

Seja o que for que você adicione, termine com `./gradlew contentCheck` (`-PtoolArgs="--quiet"`
pula a impressão do grafo). Ele carrega os arquivos exatamente como a instalação de um jogador,
roda o validador e a checagem de strings, imprime o grafo de desbloqueios e o caminho mais barato
até cada desbloqueável, e falha a qualquer erro. Uma falha nomeia o arquivo, um ponteiro JSON e a
regra, por exemplo `upgrades.json#/nodes/11/prereqs/0: unknown upgrade node 'scholar_2'` — a
décima segunda entrada de `nodes`, sua primeira entrada de `prereqs`. Todo id de conteúdo precisa
de `<kind>.<id>.name` e `.desc` tanto em `en.json` quanto em `pt_BR.json`; os dois arquivos
precisam ter exatamente o mesmo conjunto de chaves.

## Esta wiki

A wiki não é editada no GitHub: as fontes dela são a pasta `wiki/` no `main`, revisadas como
qualquer outra mudança, e `.github/workflows/wiki.yml` as publica na wiki do GitHub a cada push que
toca `wiki/`. Um pull request que altera `wiki/` roda `scripts/check-wiki.sh`, que verifica os
links entre páginas (os de colchetes duplos), as imagens em `wiki/images/`, a linha de troca de
idioma no topo de cada página e a barra lateral. **Nunca edite uma página pela interface web** —
a próxima sincronização a sobrescreve. Toda página existe em inglês e em português do Brasil
(`…-(pt-BR).md`).

## Leitura adicional

| Documento | Conteúdo |
| --- | --- |
| [`docs/README.md`](https://github.com/michelbr84/Flapforge/blob/main/docs/README.md) | o índice da documentação de engenharia e design |
| [`CONTRIBUTING.md`](https://github.com/michelbr84/Flapforge/blob/main/CONTRIBUTING.md) | o fluxo completo, as regras de código e a lista de verificação |
| [`CODE_OF_CONDUCT.md`](https://github.com/michelbr84/Flapforge/blob/main/CODE_OF_CONDUCT.md) | Contributor Covenant 2.1 |
| [`SECURITY.md`](https://github.com/michelbr84/Flapforge/blob/main/SECURITY.md) | escopo e como relatar uma vulnerabilidade |
| [`CHANGELOG.md`](https://github.com/michelbr84/Flapforge/blob/main/CHANGELOG.md) | todas as versões, mais o histórico herdado do projeto original |
