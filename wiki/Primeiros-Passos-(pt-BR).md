_Idioma:_ [[English|Getting-Started]] · **Português (Brasil)**

# Primeiros passos

Flapforge é um roguelite de arcade baseado em habilidade: bata as asas, desvie, sobreviva,
pontue — e leve as moedas, o XP, as aves e as melhorias que você ganha de uma partida para a
seguinte. Esta página cobre o que o jogo exige, as formas de executá-lo, as flags de
inicialização, onde ele guarda suas configurações e seu save, e o que esperar na primeira vez
que ele abre. Como jogar depois de entrar está em [[Controles|Controles-(pt-BR)]] e em
[[Jogando uma partida|Jogando-uma-Partida-(pt-BR)]].

## Requisitos

| Necessário | Detalhes |
| --- | --- |
| Java | **JDK 17 ou mais novo** — qualquer distribuição (Temurin, Microsoft, Zulu, o `openjdk-17-jdk` da sua distro, ...). O projeto é compilado com `--release 17`, então um JDK mais novo funciona. O fat jar precisa apenas de um JRE/JDK 17+; as imagens de aplicativo empacotadas são autossuficientes. |
| Gradle | **Nada a instalar.** O repositório traz o Gradle wrapper (`gradlew` / `gradlew.bat`), que baixa o Gradle 9.7.1 no primeiro uso. Só é necessário para compilar a partir do código-fonte. |
| Desktop | Linux (X11 ou Wayland), Windows 10+ ou macOS 12+. Flapforge é um aplicativo AWT/Java2D puro: sem OpenGL, sem bibliotecas nativas, sem motor de jogo. |
| Android | Um celular ou tablet com Android 13 ou mais novo (o mínimo do APK é a API 33). |
| Git | Só se você for clonar o repositório do código-fonte. |

Verifique o Java do seu `PATH` antes de qualquer outra coisa:

```bash
java -version
```

## Formas de executar

### Downloads de release

Toda release marcada com tag em
[github.com/michelbr84/Flapforge/releases](https://github.com/michelbr84/Flapforge/releases)
traz o mesmo conjunto de arquivos, compilados e testados pelo CI nos três sistemas de desktop:

| Arquivo | O que é | Como executar |
| --- | --- | --- |
| `Flapforge-<version>-linux.zip` | imagem de aplicativo autossuficiente (`Flapforge/`) | descompacte e abra o lançador `Flapforge` que está dentro |
| `Flapforge-<version>-macos.zip` | imagem de aplicativo autossuficiente (`Flapforge.app`) | descompacte e abra `Flapforge.app` |
| `Flapforge-<version>-windows.zip` | imagem de aplicativo autossuficiente (`Flapforge/`) | descompacte e abra o lançador `Flapforge` que está dentro |
| `flapforge-<version>-all.jar` | o fat jar (inclui o Gson) | `java -jar flapforge-<version>-all.jar` — exige um JRE/JDK 17+ |
| `Flapforge-<version>-android.apk` | a versão Android, para instalação manual (sideload) | copie para o aparelho, permita instalações dessa origem e abra |

As imagens de aplicativo trazem o ícone da sua plataforma (`.png` / `.icns` / `.ico`). As flags de
inicialização vão depois do nome do jar: `java -jar flapforge-<version>-all.jar --fullscreen
--no-audio`.

### A partir do código-fonte

```bash
git clone https://github.com/michelbr84/Flapforge.git
cd Flapforge
./gradlew run                                   # Windows: gradlew.bat run
./gradlew run --args="--seed 42 --scale 2"      # as flags de inicialização vão em --args
```

A primeira execução baixa o Gradle e as duas dependências (Gson, JUnit); depois disso,
`./gradlew --offline build` funciona sem rede. `./gradlew build` compila tratando todo aviso de
lint como erro e roda a suíte de testes padrão — veja
[[Compilando e contribuindo|Compilando-e-Contribuindo-(pt-BR)]] para as outras tarefas do Gradle.

### Fat jar e imagem de aplicativo

```bash
./gradlew fatJar
java -jar build/libs/flapforge-0.2.2-all.jar
java -jar build/libs/flapforge-0.2.2-all.jar --fullscreen --no-audio
java -jar build/libs/flapforge-0.2.2-all.jar --lang pt_BR
```

`scripts/package.sh` (Linux/macOS, ou Git Bash no Windows) monta o fat jar, exporta o ícone
desenhado proceduralmente em três formatos e roda o `jpackage` para gravar uma imagem de
aplicativo autossuficiente em `build/dist/` — `Flapforge/` no Linux, `Flapforge.app` no macOS,
`Flapforge/` no Windows. O `jpackage` vem com o JDK 14+; o script encerra com uma mensagem clara
quando não encontra um.

### Scripts

| Script | Faz |
| --- | --- |
| `scripts/run.sh` / `scripts\run.ps1` | inicia o jogo pelo Gradle e repassa todos os argumentos |
| `scripts/build.sh` / `scripts\build.ps1` | roda `build fatJar` |
| `scripts/package.sh` | produz a imagem de aplicativo descrita acima |

```bash
scripts/run.sh --seed 42 --world wind_valley --bird zephyr   # --world exige um mundo já seu
```

## Flags de inicialização

As flags são interpretadas antes de qualquer outra coisa. Passe-as por `--args="..."`, pelos
scripts de execução ou diretamente ao fat jar. Uma flag desconhecida ou um valor malformado
imprime a mensagem e o texto de uso, e o jogo não inicia; sem um display, uma inicialização com
janela imprime `No display available; use --headless-run N or --no-window.`

| Flag | Significado |
| --- | --- |
| `--seed N` | semente fixa do gerador aleatório, para uma partida reproduzível |
| `--world ID` | seleciona um mundo para esta inicialização (`green_fields`, `wind_valley`, `iron_forge`, `storm_sky`, `void`). Não desbloqueia nada: um mundo que você já tem é gravado na seleção do perfil, que a placa da tela inicial e INICIAR PARTIDA passam a jogar; um mundo bloqueado é recusado com uma linha na saída padrão e a seleção fica como estava. Um id desconhecido é reportado e ignorado. |
| `--bird ID` | começa com a ave indicada |
| `--tier ID` | nível de dificuldade (`normal`, `hard`, `nightmare`) |
| `--scale N` | escala inicial da janela, um múltiplo inteiro do campo de jogo de 420×640. Padrão: a maior escala cuja janela cabe na tela — 2× em um monitor da classe 1440, 1× em 1080p |
| `--fullscreen` | inicia em tela cheia sem bordas (`F11` alterna) |
| `--no-audio` | inicia em silêncio: nenhum dispositivo de som é aberto; o jogo roda exatamente igual |
| `--home DIR` | usa `DIR` no lugar do diretório padrão de configurações/save (veja abaixo) |
| `--headless-run N` | simula `N` quadros sem janela e imprime uma linha de resumo mais o hash de determinismo que o CI compara entre plataformas |
| `--no-window` | roda sem janela |
| `--help`, `-h` | imprime o texto de uso e encerra |
| `--reset-save` | começa com um perfil novo; o save antigo e seu backup são postos de lado, nunca apagados (veja abaixo) |
| `--lang CODE` | idioma da interface nesta inicialização: `auto` (idioma do sistema), `en`, `pt_BR`. Sobrepõe o idioma salvo nas configurações; um código desconhecido é ignorado e vale `auto` |

> **Dica:** `--lang pt_BR` põe toda a interface em português do Brasil nessa inicialização. O
> idioma também pode ser trocado ao vivo em Configurações, e os dois valem na hora.

## Onde ficam as configurações e os saves

O diretório do perfil é resolvido nesta ordem: `--home DIR`, depois a propriedade de sistema
`flapforge.home`, depois a variável de ambiente `FLAPFORGE_HOME`, depois o padrão de cada
sistema:

| Sistema | Diretório do perfil |
| --- | --- |
| Linux / BSD | `~/.flapforge` |
| Windows | `%APPDATA%\Flapforge` (ou `~/AppData/Roaming/Flapforge` quando a variável não existe) |
| macOS | `~/Library/Application Support/Flapforge` |

Nada cria o diretório até algo ser gravado nele. Ele guarda:

| Arquivo | Finalidade |
| --- | --- |
| `settings.json` | opções (idioma, volumes, teclas, vídeo, acessibilidade) — não é progresso |
| `save.json` | o perfil: moedas, XP, desbloqueios, melhorias, estatísticas, recordes |
| `save.json.bak` | o perfil de ontem: gravado uma vez por sessão, logo depois de um carregamento bem-sucedido |
| `backups/save.v<N>.pre-migration.json` | o save como estava antes de uma migração de esquema a partir da versão `N` |
| `save.corrupt-<time>.json` | um save que não pôde ser lido, posto de lado por um carregamento que falhou |
| `save.reset-<time>.json` | o save que você pediu para `--reset-save` abandonar |

Toda gravação é à prova de travamento (arquivo temporário, fsync, renomeação atômica), e a regra
que tudo segue é que **o jogo nunca destrói o seu progresso**: um travamento no meio da gravação,
um arquivo corrompido, um downgrade ou um bug numa migração põem um arquivo de lado; nada apaga
nenhum.

`--reset-save` começa um perfil novo e põe o save antigo e seu backup de lado como
`save.reset-<time>.json` e `save.bak.reset-<time>.json`. Se um dia você quiser o progresso antigo
de volta, feche o jogo, renomeie o arquivo de reset para `save.json` e abra de novo.

Um `settings.json` gravado por outra versão do jogo não é carregado: os padrões são restaurados,
o arquivo antigo fica guardado como `settings.v<N>.json` e um aviso na tela conta isso.

## Sua primeira inicialização

1. **Abertura.** Uma tela de abertura rápida enquanto os arquivos de conteúdo são validados e a
   fonte incluída é instalada.
2. **A tela inicial.** Seu cartão de jogador (avatar, nível, XP), a ficha de moedas, a engrenagem
   de configurações, a placa do mundo, a cena da forja, o cartão **Próximo desbloqueio**, o botão
   dourado **INICIAR PARTIDA** com o mundo e a dificuldade que ele vai jogar, e a navegação de
   baixo — **Loja**, **Aves**, **Jogar**, **Forja**, **Metas**. Até você voar uma vez, ela diz
   "Nenhuma partida ainda — toque em INICIAR PARTIDA". O passeio completo está em
   [[Tela inicial|Tela-Inicial-(pt-BR)]].
3. **INICIAR PARTIDA.** Um perfil novo voa com Asa-forjada (Forgewing) e Batida Dupla (Double
   Flap) em Campos Verdes (Green Fields), na dificuldade Normal. Bata as asas com `Space`,
   `Up arrow` ou o botão esquerdo do mouse; a primeira batida começa a partida. Mesmo uma partida
   que termina no portão 0 paga cerca de 50 moedas, o bastante para começar na
   [[Loja e melhorias|Loja-e-Melhorias-(pt-BR)]]. O ciclo inteiro — portões, escolhas de
   modificador, chefes, a faixa de fim de jogo e o resumo — está em
   [[Jogando uma partida|Jogando-uma-Partida-(pt-BR)]].

`Esc` na tela inicial pergunta "Pressione de novo para sair"; fique vinte segundos parado ali e
uma partida de demonstração roda ao fundo até você pressionar qualquer coisa.
