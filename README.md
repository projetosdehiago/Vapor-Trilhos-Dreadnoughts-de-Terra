# Vapor & Trilhos: Dreadnoughts de Terra

Mod de Minecraft Java (Fabric) de sobrevivência nômade com estética steampunk rústica:
veículos terrestres pesados — *landships* — sobre esteiras de madeira reforçada e ferro,
movidos por uma caldeira a vapor. É como os barcos grandes do jogo, mas para terra, e
personalizável com módulos (cama, baús de carga, fornalha de alta temperatura e compactador).

> **Status:** versão **1.0.0**. As 3 fases estão prontas: o landship anda, a caldeira funciona, solta vapor e se
> desgasta (Fase 1), recebe cama, baús, fornalha e compactador (Fase 2) e pode ser montado e
> desmontado a partir de blocos (Fase 3).
> O plano completo, as regras de jogo e os valores de balanceamento estão em
> [`design/design.md`](design/design.md).

## Como jogar (Fase 1)

1. **Fabrique** (receitas no livro de receitas ou em `design/design.md`):
   Esteira Reforçada ×2 → Caldeira a Vapor → **Landship (Cabine Básica)**.
2. **Coloque** o landship no chão (clique direito com o item).
3. **Abasteça:** clique direito no landship com balde ou garrafa d'água (tanque de 8 baldes) e
   com carvão, madeira ou outro combustível.
4. **Acenda** com isqueiro (pederneira) ou carga de fogo, ou pelo botão do painel.
5. Espere ~20 s para ferver e mais ~10 s até **2 bar**. Clique direito para **embarcar**.
6. **Dirija** com W/S e gire com A/D (as esteiras giram no lugar). Shift desembarca.

| Tecla | Ação |
|---|---|
| W / S | Acelerar / frear e dar ré |
| A / D | Girar |
| E (embarcado) ou Shift + clique direito | Painel da caldeira |
| R | Abafador: Fechado → Normal → Aberto |
| V | Válvula de alívio (−2 bar; cega quem está perto, fora do veículo) |
| H | Apito (o som se escolhe na aba **Apito** do painel: a vapor, buzina, sino, corneta ou personalizado) |
| C | Liga/desliga o compactador |
| J ×2 (de fora, olhando para o landship) | Desmonta em blocos (Fase 3); o primeiro toque pede confirmação |

- **Cuidado com a pressão:** acima de 10 bar a válvula de segurança solta vapor que cega todos
  em volta (inclusive você) e desgasta o casco. Use o abafador e o acelerador.
- **Sem água com o fogo aceso** a caldeira superaquece. Pôr água numa caldeira seca acima de
  200 °C causa um choque térmico.
- **Conserte** com lingote/pepita/bloco de ferro, lingote de cobre ou Kit de Reparo. Madeira só
  remenda até 60 %.
- **Plataforma:** o teto do landship é sólido. Dá para subir nele, e quem está em pé em cima anda
  e gira junto com o veículo.
- **Recolher:** Shift + clique direito com a Chave de Caldeireiro, com todos fora do veículo e a
  caldeira fria. O item guarda a integridade e a água.

## Módulos (Fase 2)

Fabrique os módulos (receitas no livro de receitas ou em `design/design.md`, seção A7) e, com o
landship **parado**, **Shift + clique direito** com o módulo na mão para instalar. Eles ocupam
os 6 encaixes do deque, da frente para trás (o compactador vai na frente). Cada módulo deixa o
landship 3 % mais lento. **Shift + clique direito com a Chave de Caldeireiro** tira o último
módulo instalado e devolve o conteúdo dele; sem módulos, a chave recolhe o veículo.

| Módulo | Limite | Como usar |
|---|---|---|
| Cama Móvel | 1 | Painel → aba **Módulos** → **Dormir** (à noite, parado). Conta para pular a noite e vira seu ponto de renascimento, que só vale enquanto o landship existir. |
| Baú de Carga | 4 | Painel → aba **Carga** (27 espaços por baú; botões 1–4 trocam de baú). Funciona a bordo ou a até 5 blocos. |
| Fornalha de Alta Temperatura | 1 | Painel → aba **Fornalha**. Funde 2× mais rápido (5 s por item) com o calor da caldeira (acesa, ≥ 100 °C), gastando o combustível dela. |
| Compactador Frontal | 1 | Tecla **C** ou botão na aba Módulos. Andando para a frente, tira terra e cascalho do caminho (vão para os baús), esmaga plantas, transforma o chão em caminho de terra e tapa buracos com terra/cascalho dos baús. |

Se o landship for destruído, os módulos e o conteúdo dos baús e da fornalha caem no chão.

## Montar com blocos (Fase 3)

Em vez de fabricar o item Landship, dá para montá-lo no mundo. Coloque os blocos assim (vista de
cima; a frente é para onde você olhava ao colocar o **Leme de Controle**):

```
camada de baixo (no chão)          camada de cima
          [Compactador]            (compactador opcional, 1 bloco à frente)
[Esteira] [Chassi]  [Esteira]      [módulo] [Leme]    [módulo]
[Esteira] [Chassi]  [Esteira]      [módulo] [ vazio ] [módulo]
[Esteira] [Chassi]  [Esteira]      [módulo] [Caldeira][módulo]
```

Os módulos (cama, baú, fornalha) são opcionais. Clique com a **Chave de Caldeireiro** no Leme:
se estiver tudo certo, os blocos viram o landship, com os módulos e o conteúdo dos baús. Se
faltar algo, a mensagem diz o quê e sai fumaça dos blocos errados.

Para **desmontar**: painel → aba **Módulos** → **Desmontar em blocos**, ou olhe para o landship
e aperte **J** duas vezes (em até 3 s; o primeiro toque só pede confirmação). Precisa estar parado,
sem ninguém a bordo, com a caldeira fria e o casco 100 % reparado. O combustível e a fornalha
voltam para você; a água do tanque se perde.

Receitas novas: **Chassi de Landship** (`PIP` / `IPI` / `PIP`, dá 4) e **Leme de Controle**
(`SXS` / `PIP`, X = bússola). A Esteira, a Caldeira e os módulos também podem ser colocados
como blocos; o Baú de Carga como bloco funciona como um baú comum.

## Versão Bedrock (em desenvolvimento)

A pasta [`bedrock/`](bedrock/) tem o add-on para o Minecraft Bedrock 26.50 (PC e celular), com
as mesmas regras da Parte A do [`design/design.md`](design/design.md). Está na **Fase B1**: o
landship anda, a caldeira funciona, solta vapor, se desgasta e é consertado. Módulos e montagem
vêm nas Fases B2 e B3.

**Instalar:** abra o `vapor-trilhos-bedrock-<versão>.mcaddon` (artefato do GitHub Actions ou
`bedrock/dist/` depois de compilar). O jogo importa os dois pacotes; ative-os no mundo.
Não precisa ligar nenhum recurso experimental.

| No Java | No Bedrock |
|---|---|
| W/A/S/D | Igual (no celular, o analógico) |
| E (painel) | **Painel de Comando** na mão (vidro, cobre e redstone) ou agachar + clicar no landship. A bordo, o inventário abre o compartimento de combustível |
| R, V, H | Botões do painel; **pular enquanto pilota** toca o apito |
| Medidores na tela | Linha acima da barra de itens, para quem está a bordo |

Compilar (precisa do Node.js 22):

```bash
cd bedrock
npm ci
npm test          # caldeira e direção
npm run build     # gera bedrock/dist/vapor-trilhos-bedrock-<versão>.mcaddon
npm run check     # confere as referências entre os arquivos do add-on
```

## Requisitos

| Componente | Versão |
|---|---|
| Minecraft Java | 26.3 |
| Fabric Loader | 0.19.5 ou mais novo |
| Fabric API | 0.162.0+26.3 ou mais novo |
| GeckoLib (Fabric) | 5.5.7 ou mais novo |
| Java | 25 |

Idiomas: português do Brasil e de Portugal (mesmo texto) e inglês (EUA, Reino Unido,
Austrália, Canadá e Nova Zelândia, mesmo texto).

## Instalar para jogar

1. Instale o [Fabric Loader](https://fabricmc.net/use/) para o Minecraft 26.3.
2. Coloque na pasta `mods`:
   - o `.jar` deste mod (página de *Releases* do GitHub, ou `build/libs/` depois de compilar);
   - a [Fabric API](https://modrinth.com/mod/fabric-api) para 26.3;
   - o [GeckoLib](https://modrinth.com/mod/geckolib) para Fabric 26.3.
3. Em multiplayer, o servidor e todos os jogadores precisam dos três arquivos.

## Compilar

Precisa do **JDK 25**. O Gradle é baixado automaticamente pelo wrapper.

```bash
./gradlew build        # gera build/libs/vapor-trilhos-<versão>.jar
./gradlew runClient    # abre o cliente de desenvolvimento com o mod
./gradlew runServer    # servidor de desenvolvimento
```

Sem acesso ao repositório Maven do GeckoLib (rede restrita), compile o GeckoLib a partir do
[código-fonte](https://github.com/bernie-g/geckolib) com `./gradlew :fabric:publishToMavenLocal`
e rode o build deste mod com `-Pvt.localGeckolib=true`.

## Modelo 3D

O modelo do landship (formato Bedrock/GeckoLib, abre no Blockbench) fica em
[`design/model/`](design/model/) e é gerado por código:

```bash
pip install pillow
python3 design/model/gen_landship.py                  # gera geometria, animações e textura
python3 design/model/build_preview.py preview.html    # visualizador 3D no navegador
```

O gerador falha se duas faces ficarem sobrepostas no mesmo plano (isso cintila no jogo).
As texturas dos itens (32×32) e dos blocos também são geradas por código:
`python3 design/textures/gen_item_textures.py` e `python3 design/textures/gen_block_textures.py`.
Os itens usam o kit de `design/textures/pixelkit.py` (formas sombreadas com luz de cima à
esquerda e contorno colorido) mais detalhes colocados à mão.
Os sons dos apitos também: `python3 design/audio/gen_whistles.py` (precisa de numpy e ffmpeg).

## Desenvolvimento

- Branches por funcionalidade, commits no padrão [Conventional Commits](https://www.conventionalcommits.org/),
  um PR por fase, com descrição e passos de teste.
- O GitHub Actions compila o mod, roda os testes JUnit e os GameTests de servidor a cada push e
  PR, roda o GameTest de cliente numa tela virtual (prints ficam como artefato), confere se o
  modelo gerado está em dia e monta e confere o add-on Bedrock (o `.mcaddon` fica como artefato).

### Testes

```bash
./gradlew test               # caldeira (JUnit)
./gradlew runGameTest        # servidor: caldeira, direção, plataforma, módulos, cama, montagem...
./gradlew runClientGameTest  # cliente: pilota, usa os módulos, dorme, renasce e tira prints (build/run/clientGameTest/screenshots)
```

Em Linux sem tela: `SDL_VIDEO_FORCE_EGL=1 xvfb-run -a -s "-screen 0 1280x720x24+32" ./gradlew runClientGameTest`.

## Licença

[MIT](LICENSE).
