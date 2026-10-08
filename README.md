# Vapor & Trilhos: Dreadnoughts de Terra

Mod de Minecraft Java (Fabric) de sobrevivência nômade com estética steampunk rústica:
veículos terrestres pesados — *landships* — sobre esteiras de madeira reforçada e ferro,
movidos por uma caldeira a vapor. É como os barcos grandes do jogo, mas para terra, e
personalizável com módulos (cama, baús de carga, fornalha de alta temperatura e compactador).

> **Status:** em desenvolvimento (Fase 0 — estrutura do projeto). Ainda não há conteúdo jogável.
> O plano completo, as regras de jogo e os valores de balanceamento estão em
> [`design/design.md`](design/design.md).

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
- O GitHub Actions compila o mod a cada push e PR e confere se o modelo gerado está em dia.

## Licença

[MIT](LICENSE).
