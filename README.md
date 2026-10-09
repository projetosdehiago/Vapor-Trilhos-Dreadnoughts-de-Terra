# Vapor & Trilhos: Dreadnoughts de Terra

Mod de Minecraft Java (Fabric) de sobrevivência nômade com estética steampunk rústica:
veículos terrestres pesados — *landships* — sobre esteiras de madeira reforçada e ferro,
movidos por uma caldeira a vapor. É como os barcos grandes do jogo, mas para terra, e
personalizável com módulos (cama, baús de carga, fornalha de alta temperatura e compactador).

> **Status:** em desenvolvimento. Fase 1 (núcleo) pronta: o landship anda, a caldeira funciona,
> solta vapor e se desgasta. Módulos (Fase 2) e montagem com blocos (Fase 3) vêm depois.
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
| H | Apito |

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

## Requisitos

| Componente | Versão |
|---|---|
| Minecraft Java | 26.3 |
| Fabric Loader | 0.19.5 ou mais novo |
| Fabric API | 0.162.0+26.3 ou mais novo |
| GeckoLib (Fabric) | 5.5.7 ou mais novo |
| Java | 25 |

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

## Desenvolvimento

- Branches por funcionalidade, commits no padrão [Conventional Commits](https://www.conventionalcommits.org/),
  um PR por fase, com descrição e passos de teste.
- O GitHub Actions compila o mod, roda os testes JUnit e os GameTests de servidor a cada push e
  PR, roda o GameTest de cliente numa tela virtual (prints ficam como artefato) e confere se o
  modelo gerado está em dia.

### Testes

```bash
./gradlew test               # caldeira (JUnit)
./gradlew runGameTest        # servidor: interação, combustível, válvula, reparo, direção...
./gradlew runClientGameTest  # cliente: abre o jogo, pilota e tira prints (build/run/clientGameTest/screenshots)
```

Em Linux sem tela: `SDL_VIDEO_FORCE_EGL=1 xvfb-run -a -s "-screen 0 1280x720x24+32" ./gradlew runClientGameTest`.

## Licença

[MIT](LICENSE).
