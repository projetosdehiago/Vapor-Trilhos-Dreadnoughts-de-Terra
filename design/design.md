# Vapor & Trilhos: Dreadnoughts de Terra — Documento de Design

> **Status:** rascunho v0.1 — arquitetura Java *proposta, aguardando aprovação*.
> Este documento é a fonte para a versão Java (Fabric) **e** para uma futura versão Bedrock.
> Por isso ele é dividido em duas partes:
>
> - **Parte A — Ideia (independente de plataforma):** conceito, regras, números e receitas.
>   Tudo aqui é expresso em blocos, segundos, °C, bar e mB, sem citar APIs.
> - **Parte B — Implementação Java:** como a Parte A vira código no Fabric 26.3.
>
> Se a Parte A mudar, a Parte B deve acompanhar. A Parte B nunca deve introduzir regra de jogo
> que não esteja na Parte A.

---

# PARTE A — IDEIA (agnóstica de plataforma)

## A1. Conceito

Sobrevivência nômade com estética steampunk rústica. O jogador constrói um **landship**:
um veículo terrestre pesado sobre **esteiras de madeira reforçada e ferro**, movido por uma
**caldeira a vapor**. É o "barco grande" da terra firme: lento, robusto, e uma base móvel que
recebe **módulos** (cama, baús de carga, fornalha de alta temperatura, compactador).

O ciclo de jogo: **coletar combustível e água → manter a caldeira saudável → viajar →
consertar o desgaste → expandir com módulos**. A logística de água é o gargalo principal
(o mundo nômade obriga a planejar rotas por rios e lagos).

## A2. O veículo

| Propriedade | Valor |
|---|---|
| Pegada (footprint) | 3 × 3 blocos (quadrada — gira sem mudar a área ocupada) |
| Altura | 2 blocos (casco) — módulos e chaminé são só visuais acima disso |
| Ocupantes | 1 piloto + 2 passageiros |
| Subida de degrau | 1 bloco (como cavalo) — esteiras sobem degraus, não pulam |
| Integridade máxima | 200 pontos |
| Flutua? | **Não.** Afunda em água funda; ver A3.6 |
| Encaixes de módulo | 6 gerais + 1 frontal (só compactador) |

### A2.0 Visual

Fonte do modelo: `design/model/` (geometria Bedrock, a mesma que o GeckoLib e o Bedrock usam).
Cabine aberta com teto de cobre na frente e lanterna, leme de navio em pé sobre um pedestal
virado para o piloto, bancos no meio, caldeira verde com cintas de cobre, manômetro e chaminé
atrás, dois cilindros verticais com pistões entre bancos e caldeira, faróis na frente. Esteiras
com saia blindada por fora cobrindo a metade de cima; as rodas de apoio aparecem embaixo. Os 6 encaixes de módulo ficam nas laterais do deque (células 3×3); o
compactador é um rolo à frente. Altura visual até o topo da chaminé: 3,4 blocos.

### A2.1 Controles (padrão; todos reconfiguráveis)

| Ação | Tecla padrão | Efeito |
|---|---|---|
| Acelerar / frear-ré | W / S | Ajusta a velocidade alvo; S com o veículo parado dá ré |
| Girar | A / D | Esteiras giram no lugar (pivot) ou fazem curva em movimento |
| Desembarcar | Shift | Padrão do jogo |
| Painel do landship | E (inventário) enquanto embarcado | Abre caldeira + módulos |
| Abafador | R | Cicla Fechado → Normal → Aberto |
| Válvula de alívio | V | Solta vapor manualmente (−2 bar) |
| Apito | H | Som apenas (diversão / sinalização multiplayer) |
| Compactador | C | Liga/desliga o compactador frontal |

Interação de fora do veículo:

| Ação | Efeito |
|---|---|
| Usar (mão vazia) | Embarcar |
| Usar com balde d'água / garrafa d'água | Abastece o tanque |
| Usar com combustível | Coloca no compartimento de combustível |
| Usar com isqueiro (pederneira) ou carga de fogo | Acende a fornalha |
| Usar com item de reparo | Repara (A5) |
| Agachar + usar com módulo | Instala no primeiro encaixe compatível livre (veículo parado) |
| Agachar + usar com Chave de Caldeireiro | Remove o último módulo instalado; sem módulos e com caldeira fria, recolhe o veículo como item |
| Agachar + usar (mão vazia) | Abre o painel |
| Atacar | Causa dano à integridade (não quebra em 3 socos como barco) |

## A3. Caldeira

A caldeira tem três estoques e duas grandezas derivadas:

- **Água** (tanque): 0 – 8.000 mB (8 baldes).
- **Combustível**: compartimento com 3 espaços de itens + o item queimando agora.
- **Fogo**: aceso/apagado, com o **Abafador** em Fechado / Normal / Aberto.
- **Temperatura** (°C) e **Pressão** (bar) — calculadas a cada instante.

### A3.1 Combustível

Qualquer item queimável do jogo vale, com a mesma duração de queima da fornalha comum
(ex.: carvão = 80 s, bloco de carvão = 800 s, balde de lava = 1.000 s, tábua = 15 s).
O abafador muda o ritmo de consumo:

| Abafador | Calor | Consumo de combustível |
|---|---|---|
| Fechado | 0 (brasa: mantém o fogo aceso, não gera calor) | ×0,25 |
| Normal | +4 °C/s | ×1 |
| Aberto (tiragem forçada) | +6 °C/s | ×2 |

Sem combustível o fogo apaga. Reacender exige isqueiro, carga de fogo ou o botão do painel
(o botão só funciona se houver combustível).

### A3.2 Temperatura, pressão e água

| Parâmetro | Valor |
|---|---|
| Temperatura ambiente | 20 °C |
| Resfriamento sem fogo | −2 °C/s (até o ambiente) |
| Ponto de ebulição | 100 °C |
| Geração de vapor (T ≥ 100 °C, com água, Abafador Normal) | +0,20 bar/s |
| Geração de vapor (Abafador Aberto) | +0,30 bar/s |
| Água consumida por vapor gerado | 10 mB a cada 0,1 bar (→ 20 mB/s no Normal) |
| Perda de pressão parado (vazamentos) | −0,02 bar/s |
| Perda de pressão sem fogo | −0,10 bar/s |
| Pressão mínima para andar | 2 bar |
| Potência total | 8 bar |
| Zona vermelha | ≥ 8 bar (aviso visual/sonoro) |
| Válvula de segurança automática | 10 bar → ventila até 8 bar (**superaquecimento por pressão**) |
| Pressão máxima absoluta | 12 bar |

Com água no tanque, a temperatura fica presa em `100 °C + 10 °C × pressão` (máx. 220 °C):
o calor extra vira vapor. **Sem água**, todo o calor sobe a temperatura (sem teto).

Tempo de partida a frio (Abafador Normal): ~20 s para ferver + ~10 s até 2 bar ≈ **30 s**.
Autonomia de água: 8.000 mB ÷ 20 mB/s ≈ **6,7 min de fogo Normal contínuo**.

### A3.3 Consumo de vapor pelo motor

| Situação | Consumo |
|---|---|
| Motor em marcha lenta (parado, fogo aceso) | incluído na perda de 0,02 bar/s |
| Andando | 0,15 bar/s × (velocidade ÷ velocidade máxima) |
| Girando no lugar (pivot) | 0,05 bar/s |
| Subir um degrau de 1 bloco | 0,3 bar (instantâneo) |
| Compactador ativo | +0,03 bar/s |
| Fornalha de alta temperatura trabalhando | −0,05 bar/s da geração (rouba calor) |

Equilíbrio: no Normal, andando a toda (0,17 bar/s) a pressão ainda sobe devagar (+0,03 bar/s);
o jogador precisa **gerenciar abafador e acelerador** para não chegar a 10 bar.

### A3.4 Superaquecimento e vapor cegante

Três gatilhos:

1. **Sobrepressão** (≥ 10 bar): a válvula de segurança ventila até 8 bar.
   Nuvem de vapor por 3 s, **Cegueira 3 s** em todos os jogadores num raio de 5 blocos
   (inclusive ocupantes), **−5 de integridade** por evento.
2. **Caldeira seca** (água = 0 e T > 120 °C): fumaça/vapor contínuos, **Cegueira 3 s** renovada
   a cada 2 s no raio de 5 blocos, **−1 integridade/s**; acima de 300 °C, **−4 integridade/s**.
3. **Choque térmico** (adicionar água com tanque vazio e T ≥ 200 °C): explosão de vapor
   (sem dano a blocos), **Cegueira 5 s** num raio de 6 blocos, **−15 de integridade**,
   temperatura cai para 100 °C.

**Válvula de alívio manual (V):** −2 bar, nuvem de vapor por 2 s, Cegueira 3 s a jogadores
num raio de 4 blocos **que não estejam no veículo** (ferramenta tática em multiplayer),
sem desgaste. Recarga de 5 s.

### A3.5 Quando o veículo para

- Pressão < 2 bar → sem tração (rola até parar por atrito).
- Sem combustível → fogo apaga → pressão cai 0,10 bar/s.
- Sem água → não gera vapor → pressão cai; e entra em "caldeira seca" se o fogo continuar.

### A3.6 Água e terreno

- Água rasa (≤ 1 bloco de profundidade): anda a 50 %.
- Água funda: o veículo afunda devagar. Se a caldeira ficar submersa (água acima de 1,5
  bloco do chão do veículo) o **fogo apaga**.
- Lava: −1 integridade/s; fogo nos ocupantes como de costume.

## A4. Movimento

| Parâmetro | Valor |
|---|---|
| Velocidade máxima (frente) | 5 m/s (≈ entre andar e correr do jogador) |
| Velocidade máxima (ré) | 2 m/s |
| Aceleração | 1 m/s² (0 → 5 m/s em 5 s) |
| Frenagem | 2,5 m/s² |
| Giro parado (pivot) | 45 °/s |
| Giro na velocidade máxima | 30 °/s |
| Fator de potência | `clamp((pressão − 2) ÷ 6, 0, 1)` multiplica a velocidade máxima |
| Massa dos módulos | −3 % de velocidade máxima por módulo instalado |

Multiplicador de terreno (bloco sob o centro do veículo):

| Terreno | Multiplicador |
|---|---|
| Caminho de terra, cascalho, pedra e derivados, madeira | ×1,0 |
| Grama, terra, podzol, micélio | ×0,9 |
| Areia, neve, cascalho fino de areia vermelha | ×0,7 |
| Areia das almas, lama | ×0,5 |
| Água rasa | ×0,5 |
| Gelo | ×1,0, mas com pouca aderência (derrapa) |

Esteiras em terreno irregular: o veículo **inclina** (arfagem e rolagem) conforme a altura do
chão sob os quatro cantos das esteiras, suavizado. Inclinação é visual — não muda a colisão.
Rampas acima de 1 bloco de desnível bloqueiam o avanço.

## A5. Integridade (desgaste) e reparo

Integridade 0 – 200. Fontes de desgaste:

| Fonte | Desgaste |
|---|---|
| Distância percorrida | 0,02 por bloco (≈ 10.000 blocos de 200 → 0) |
| Ventilação por sobrepressão | 5 por evento |
| Caldeira seca | 1/s (4/s acima de 300 °C) |
| Choque térmico | 15 |
| Colisão frontal a ≥ 3 m/s | (velocidade − 2) × 4 |
| Queda | (altura − 3) × 5 |
| Ataques | dano da arma × 1 (explosões × 2) |
| Lava | 1/s |
| Compactador | 0,05 por bloco processado |

Efeitos:

| Integridade | Efeito |
|---|---|
| < 50 % | Vazamento: +25 % de consumo de água; faíscas ocasionais |
| < 25 % | Velocidade máxima −30 %; fumaça preta constante |
| 0 | **Destruído:** dropa 2 Esteiras Reforçadas, 6 pepitas de ferro, 4 tábuas, todos os módulos e todo o conteúdo dos inventários. A cabine e a caldeira são perdidas. |

Reparo (usar o item no veículo, ou espaço de reparo no painel; 0,5 s entre usos;
não funciona durante superaquecimento):

| Item | Reparo | Limite |
|---|---|---|
| Lingote de ferro | +10 | até 100 % |
| Pepita de ferro | +1 | até 100 % |
| Bloco de ferro | +90 | até 100 % |
| Lingote de cobre | +6 | até 100 % |
| Tábua (qualquer) | +3 | **até 60 %** |
| Tronco (qualquer) | +8 | **até 60 %** |
| Kit de Reparo | +60 | até 100 % |

Madeira só remenda até 60 %: a estrutura exige metal para voltar ao estado de fábrica.

## A6. Módulos

Limites: até **1 Cama**, até **1 Fornalha**, até **4 Baús**; o **Compactador** só no encaixe
frontal. Instalar/remover só com o veículo parado. Remover um módulo com inventário
devolve o conteúdo (vai para o inventário do jogador; o que não couber cai no chão).

### A6.1 Cama móvel

> **Decisão pendente** (ver perguntas no fim): propor **as duas funções**.

- **Dormir:** com o veículo parado (velocidade 0) e regras normais de sono (noite/tempestade,
  sem monstros a 8 blocos), o jogador pode dormir na cama do landship; conta para pular a noite.
- **Ponto de renascimento:** dormir (ou usar a cama) registra o landship como "lar" do jogador.
  Ao morrer, renasce ao lado do landship, onde quer que ele esteja. Se o landship foi
  destruído ou recolhido como item, o lar é apagado e vale o spawn padrão.

### A6.2 Baú de carga

27 espaços cada (até 4 → 108). Acessível pelo painel (aba "Carga") por qualquer ocupante ou
por quem estiver a até 5 blocos. Hoppers/funis não interagem (é uma entidade móvel).

### A6.3 Fornalha de alta temperatura

- 1 espaço de entrada, 1 de saída. **Sem espaço de combustível**: usa o calor da caldeira.
- Funciona só com a caldeira a ≥ 100 °C.
- Usa as receitas de fundição comuns, **2× mais rápida**: 5 s por item (comum: 10 s).
- Custo: cada item consome 5 s de queima do combustível da caldeira (o dobro da eficiência
  da fornalha comum) e reduz a geração de vapor em 0,05 bar/s enquanto trabalha.
- A experiência é acumulada e entregue ao retirar a saída.

### A6.4 Compactador frontal

Ligado (tecla C ou painel) e andando para frente a ≥ 0,5 m/s, a cada 0,25 s processa a faixa
de 3 blocos de largura logo à frente das esteiras:

1. **Obstáculos** (até 2 blocos de altura, no nível do veículo): blocos de terra (grama,
   terra, terra grossa, podzol, micélio, terra enraizada) e **cascalho** são removidos;
   plantas substituíveis (grama alta, flores, neve fina) são esmagadas.
2. **Chão** (a camada logo abaixo): grama/terra/terra grossa/podzol/micélio viram
   **caminho de terra**; cascalho continua cascalho.
3. **Buracos** de 1 bloco de profundidade no chão são preenchidos com terra ou cascalho
   **tirados do baú de carga** (se houver).
4. Os itens removidos vão para o baú de carga; o que não couber cai atrás do veículo.

Nunca afeta: blocos com inventário/dados, blocos fora da lista acima, áreas protegidas
(spawn protegido e mods de proteção). Custo: +0,03 bar/s e 0,05 de integridade por bloco.

## A7. Itens, blocos e receitas

Legenda: `I` lingote de ferro · `N` pepita de ferro · `C` lingote de cobre · `L` qualquer tronco ·
`P` qualquer tábua · `G` painel de vidro · `K` tijolo (item) · `B`, `S`, `T`, `X`, `Y` conforme a linha.

| ID | Nome (pt_br) | Nome (en_us) | Receita | Resultado |
|---|---|---|---|---|
| `reinforced_track` | Esteira Reforçada | Reinforced Track | `ILI` / `LNL` / `ILI` | 2 |
| `steam_boiler` | Caldeira a Vapor | Steam Boiler | `CBC` / `IXI` / `III` — B = balde, X = fornalha | 1 |
| `landship` | Landship (Cabine Básica) | Landship (Basic Cab) | `PGP` / `PBP` / `TPT` — B = Caldeira a Vapor, T = Esteira Reforçada | 1 |
| `boilermaker_wrench` | Chave de Caldeireiro | Boilermaker's Wrench | ` I ` / ` CI` / `I  ` | 1 |
| `repair_kit` | Kit de Reparo | Repair Kit | sem forma: 2 I + 2 P + 1 C + 1 N | 1 |
| `bed_module` | Módulo Cama Móvel | Mobile Bed Module | `PBP` / `IPI` — B = qualquer cama | 1 |
| `cargo_module` | Módulo Baú de Carga | Cargo Chest Module | `NXN` / `PIP` — X = baú | 1 |
| `furnace_module` | Módulo Fornalha de Alta Temperatura | High-Temperature Furnace Module | `CIC` / `KXK` / `KKK` — X = alto-forno | 1 |
| `compactor_module` | Módulo Compactador Frontal | Front Compactor Module | `IYI` / `SXS` / `III` — Y = pistão, S = pedra lisa, X = bloco de ferro | 1 |
| `landship_chassis` *(Fase 3)* | Chassi de Landship | Landship Chassis | `PIP` / `IPI` / `PIP` | 4 |
| `landship_helm` *(Fase 3)* | Leme de Controle | Control Helm | `SXS` / `PIP` — S = graveto, X = bússola | 1 |

Nota: na Fase 3, `reinforced_track`, `steam_boiler`, os módulos, o chassi e o leme também são
  **blocos colocáveis** (para a montagem).

### A7.1 Saque (loot)

- Blocos da Fase 3 dropam a si mesmos.
- Destruição do veículo: ver A5.
- **Ideia futura:** baús de vilas de ferreiro com chance de Kit de Reparo / Esteira.

## A8. Montagem a partir de blocos (Fase 3)

O jogador constrói o gabarito abaixo e usa a **Chave de Caldeireiro** no Leme. Se o gabarito
estiver válido, os blocos somem e o landship aparece no lugar, com os módulos correspondentes
(e o conteúdo dos baús transferido). Agachar + chave num landship **parado e frio** faz o
inverso (desmonta em blocos, se houver espaço livre; senão, recusa).

Camada 0 (chão), vista de cima, frente para cima:

```
            [Cf]          ← opcional: Compactador (1 bloco à frente)
        [T] [Ch] [T]
        [T] [Ch] [T]
        [T] [Ch] [T]
```

Camada 1 (sobre a camada 0):

```
        [m] [H]  [m]
        [m] [ ]  [m]      ← centro livre = assento de passageiro
        [m] [Cv] [m]
```

`T` Esteira Reforçada · `Ch` Chassi · `H` Leme (sua direção define a frente) ·
`Cv` Caldeira a Vapor · `m` encaixe de módulo opcional (cama, baú, fornalha) · `Cf` Compactador.

## A9. Ideias futuras (fora do escopo das 3 fases)

- Bomba d'água (módulo que reabastece parado sobre água ou na chuva).
- Explosão catastrófica da caldeira (desligada por padrão).
- Engates: rebocar um segundo landship / vagões.
- Biomas frios aumentam o tempo de partida.
- Canhão a vapor / módulos de combate.

---

# PARTE B — IMPLEMENTAÇÃO JAVA (Fabric)

## B1. Versões confirmadas (pesquisa em 2026-10-08)

| Componente | Versão | Fonte |
|---|---|---|
| Minecraft | **26.3** (release de 2026-09-15; `latest.release` no manifest) | piston-meta.mojang.com |
| Java | **25** (`javaVersion.majorVersion = 25`) | manifest da versão 26.3 |
| Fabric Loader | **0.19.5** (stable) | meta.fabricmc.net |
| Fabric API | **0.162.0+26.3** (mais recente para 26.3) | maven.fabricmc.net |
| Fabric Loom | **1.18.3** (plugin `net.fabricmc.fabric-loom`) | maven.fabricmc.net |
| Gradle | **9.7.1** (wrapper do mod-exemplo oficial) | FabricMC/fabric-example-mod |
| GeckoLib | **5.5.7** para Fabric 26.3 (única dependência além da Fabric API — escolha do usuário) | Modrinth / bernie-g/geckolib |
| Mappings | **nenhum** — desde a 26.1 o jogo vem sem ofuscação; usamos os nomes oficiais da Mojang direto, sem Yarn/Intermediary e sem remapeamento | ausência de `client_mappings` no manifest; `intermediary`/`yarn` sem versões 26.x |

Verificado na prática: o `fabric-example-mod` compila com essa combinação (JDK 25 instalado via
`apt` no ambiente de build).

Mod id: **`vapor_trilhos`** — válido (`^[a-z][a-z0-9_-]{1,63}$`) e sem projeto com esse slug no
Modrinth. Pacote Java: `io.github.projetosdehiago.vaportrilhos`.

## B2. Arquitetura do veículo — opções

| Critério | **A. Entidade única + módulos como upgrades** | **B. Montagem livre (blocos → entidade, estilo "contraption")** | **C. Híbrido: A + gabarito multibloco (recomendado)** |
|---|---|---|---|
| Colisão | Uma AABB quadrada 3×3; `canBeCollidedWith` como o barco (dá para andar em cima) | Forma arbitrária girando: precisa de colisão OBB/voxel própria → mixins pesados em `Entity.move` | Igual a A |
| Esteira em terreno irregular | Amostra altura nos 4 cantos; degrau de 1 bloco; inclinação só visual | Cada bloco precisa colidir com o terreno; muito caro e instável | Igual a A |
| Sincronização | `SynchedEntityData` pequeno (medidores) + menus para inventários | Precisa sincronizar a grade de blocos inteira e re-renderizar blocos arbitrários | Igual a A |
| Multiplayer | Padrão do barco (cliente do piloto simula, servidor valida) | Dessincronia de colisão entre clientes é o problema clássico desses mods | Igual a A |
| Mixins | Nenhum previsto | Muitos (colisão, render, interação) | Nenhum previsto |
| "Montar com blocos" | Não (só item) | Sim, totalmente livre | **Sim**, por gabarito fixo com encaixes |
| Esforço / risco | Baixo | Muito alto (é um mod inteiro: Create/Valkyrien Skies) | Médio |
| Portabilidade Bedrock | Boa (entidade com componentes) | Praticamente impossível no Bedrock | Boa (Bedrock tem detecção de estruturas por script) |

**Recomendação: C.** As Fases 1–2 entregam a opção A (o veículo é uma entidade só, módulos
são dados dela). A Fase 3 adiciona o gabarito multibloco que se converte nessa mesma entidade
e volta a ser blocos. Assim a física e a rede ficam simples e robustas, e o jogador ainda tem
a experiência de "construir" o landship — sem reescrever física de colisão do jogo.

Por que pegada quadrada 3×3: no Minecraft a caixa de colisão de entidade é sempre
alinhada aos eixos e com base quadrada. Um veículo retangular girando teria colisão errada
em diagonais; o quadrado 3×3 é igual em qualquer rotação. Largura real 2,9 para passar em
vãos de 3 blocos.

## B3. Desenho técnico (opção C)

### B3.1 Entidade

- `LandshipEntity extends VehicleEntity implements HasCustomInventoryScreen`
  (mesma base dos barcos/carrinhos na 26.3).
- Dimensões 2,9 × 2,0; `canBeCollidedWith → true` (jogadores sobem no teto como num barco);
  degrau via atributo de *step height* = 1,0.
- 3 assentos: posições de passageiro calculadas a partir do yaw (como o barco).
- **Movimento — padrão do barco:** quando o piloto é o jogador local, **o cliente do piloto
  simula o movimento** (`isLocalInstanceAuthoritative`) e o jogo já envia a posição do
  veículo ao servidor, que valida. Os demais clientes interpolam (`InterpolationHandler`).
  Isso evita atraso de input; o veículo é lento, então a validação do servidor é folgada.
- **Caldeira — autoridade do servidor:** toda a simulação térmica, consumo, superaquecimento,
  desgaste e o compactador rodam **só no servidor**. Os medidores vão para os clientes via
  `SynchedEntityData` (água, combustível restante, temperatura, pressão, integridade, abafador,
  flags). O cliente do piloto lê a pressão sincronizada para calcular a potência.
- Persistência com a API nova de salvamento (`ValueInput`/`ValueOutput` + `Codec`s) — nada
  de NBT manual antigo.
- Inclinação visual: o cliente amostra a altura do chão sob os 4 cantos e suaviza pitch/roll
  só no render state.

### B3.2 Simulação da caldeira (Java puro, testável)

`BoilerSimulation` é uma classe **sem dependência do Minecraft**: recebe estado + entradas
(acelerador, abafador, módulos ativos, dt) e devolve o novo estado + eventos (ventilou,
caldeira seca, choque térmico). Todos os números da Parte A ficam em `BalanceConstants`.
Benefícios: testes unitários JUnit rápidos e reaproveitamento direto como especificação para
o Bedrock.

### B3.3 Módulos

- `ModuleType` (registro próprio simples em código): BED, CARGO, FURNACE, COMPACTOR, com
  regras de encaixe e limites.
- Estado por módulo serializado com `Codec` (inventário do baú, progresso da fornalha).
- Inventários expostos como `Storage<ItemVariant>` da **Fabric Transfer API**
  (`ContainerStorage`), usado pelo compactador para depositar drops e retirar terra/cascalho
  com transações (sem duplicação nem perda).
- Tanque d'água como `SingleFluidStorage` da **Transfer API**: abastecer com balde/garrafa usa
  `FluidStorage.ITEM` + `ContainerItemContext` (funciona também com recipientes de outros mods).

### B3.4 GUI e rede

- Painel do landship: `ExtendedMenuType` (Fabric Menu API) com o id da entidade no payload de
  abertura; abas Caldeira / Carga / Fornalha / Módulos. `stillValid` checa distância (5 blocos)
  e se a entidade existe — vários jogadores podem abrir ao mesmo tempo.
- Teclas (R, V, H, C) registradas com `KeyMappingHelper` e enviadas por **payloads tipados**
  (`CustomPacketPayload` + `PayloadTypeRegistry` + `ServerPlayNetworking`). Servidor valida que
  quem envia é o piloto.
- W/A/S/D não precisam de pacote próprio (input vanilla do veículo).
- HUD de medidores com `HudElementRegistry` (só quando pilotando).

### B3.5 Cama

- Dormir: `EntitySleepEvents` (Fabric) permite validar "cama" que não é bloco de cama
  (`ALLOW_BED`) e as condições de sono — sem mixin.
- Renascimento: o "lar" fica num **Data Attachment** persistente no jogador (UUID do landship +
  última posição/dimensão conhecida); um `SavedData` do servidor mantém o índice
  landship → posição atualizada. No `ServerPlayerEvents.AFTER_RESPAWN` o jogador é teleportado
  para o lado do landship. Se algum detalhe exigir mixin, aviso antes.

### B3.6 Compactador

Roda no servidor; para cada bloco: `level.mayInteract(piloto, pos)` + evento
`PlayerBlockBreakEvents.BEFORE` (mods de proteção podem vetar). Listas por **tags** de dados:
`vapor_trilhos:compactable`, `vapor_trilhos:flattens_to_path`, `vapor_trilhos:fill_material`,
`vapor_trilhos:compactor_immune` — configuráveis por datapack.

### B3.7 Fase 3 — gabarito

Blocos próprios (chassi, leme com `FACING`, esteira, caldeira, módulos como blocos).
`AssemblyValidator` checa o gabarito a partir do leme (rotacionado pela direção dele),
coleta módulos + inventários, remove os blocos e cria a entidade num único tick do servidor.
Desmontagem: verifica espaço livre para todos os blocos antes de mexer em qualquer um.

### B3.8 Recursos

- **Data generation** (Fabric Data Generation API) para receitas, tags, loot tables, modelos e
  os dois arquivos de idioma (`en_us`, `pt_br`) — o CI garante que nada fica faltando.
- **Modelo da entidade com GeckoLib 5** (decisão do usuário): `landship.geo.json` +
  `landship.animation.json` + `landship.png` + `landship_glowmask.png`, gerados por
  `design/model/gen_landship.py` e copiados para `assets/vapor_trilhos/geckolib/models/entity/`,
  `.../geckolib/animations/entity/` e `textures/entity/`. Todos abrem no Blockbench.
  - Ossos que o código controla: `slot_*_{bed,cargo,furnace}` e `slot_front_compactor`
    (visibilidade conforme os módulos instalados), `gauge_needle` (pressão), `helm` (direção),
    `body` (arfagem/rolagem do terreno).
  - Animações por controlador independente: `track_left.*` e `track_right.*` (cada esteira
    anda para frente/ré, então girar no lugar = uma esteira em cada sentido), `engine.idle` /
    `engine.working`, `compactor.roll`, `boiler.vent`, `firebox.open`. A velocidade das
    animações acompanha a velocidade real.
  - Locators para partículas: `smoke` (chaminé), `steam_vent` (válvula), `firebox_front`,
    `seat_*` (referência dos assentos).
  - `landship_glowmask.png` faz a fornalha e a lanterna brilharem (camada automática do GeckoLib).
- Texturas de itens e blocos: PNGs placeholder gerados.
- Sons: `sounds.json` com eventos próprios (`vapor_trilhos:boiler.hiss`, `.whistle`, `.vent`,
  `.engine`) apontando para áudios vanilla existentes — funcionais sem empacotar `.ogg`.

### B3.9 Testes

| Nível | Ferramenta | O que cobre |
|---|---|---|
| Unitário | JUnit 5 | `BoilerSimulation`, regras de módulo, reparo |
| Servidor | Fabric GameTest (`runGameTest`, também no CI) | spawn, embarque, superaquecimento aplica Cegueira, compactador transforma blocos, montagem/desmontagem |
| Cliente | Fabric Client GameTest sob `xvfb-run` | abre o cliente, cria mundo, spawna e pilota o landship, abre o painel, tira screenshot |

`./gradlew build` + `runGameTest` + client gametest a cada fase, antes de abrir o PR.

### B3.10 Pacotes

```
io.github.projetosdehiago.vaportrilhos
├── VaporTrilhos                (entrypoint main)
├── registry/                   (itens, blocos, entidades, menus, sons, payloads, attachments)
├── landship/                   (LandshipEntity, seats, movement)
├── boiler/                     (BoilerSimulation, BoilerState, BalanceConstants)
├── module/                     (ModuleType, ModuleSlots, módulos)
├── assembly/                   (Fase 3)
├── menu/                       (menus server-side)
├── network/                    (payloads)
└── datagen/
client/ (source set separado)
├── VaporTrilhosClient
├── render/  (renderer + modelo da entidade)
├── screen/  (telas)
└── hud/
```

### B3.11 Referências estudadas (somente leitura; nosso código é original)

| Projeto | O que aproveitamos como ideia |
|---|---|
| Immersive Aircraft (branch 26.2, GPL-3.0) | Confirma a arquitetura: herda de `VehicleEntity` vanilla, `InterpolationHandler`, `isLocalInstanceAuthoritative`, `ValueInput/ValueOutput`; inventário com espaços tipados (combustível, melhorias, carga). Ele usa ~20 mixins (caixas de colisão extras, câmera, controles); nós evitamos isso com a pegada quadrada única. |
| Immersive Machinery (1.21.1, GPL-3.0) | A escavadora de túnel quebra blocos numa faixa 3 de largura à frente usando os vetores frente/direita/cima do veículo e um "orçamento" de quebra por tick: mesmo padrão do nosso compactador. Ele é construído sobre o Immersive Aircraft como biblioteca. |

O Immersive Aircraft **não** pode ser dependência: não existe versão para 26.3 (vai até 26.2).

## B4. Plano de entregas

| Fase | PR | Entrega |
|---|---|---|
| 0 | `chore/scaffold` | Projeto Gradle/Loom, `fabric.mod.json`, `.gitignore`, LICENSE MIT, README, GitHub Actions (build + testes em push/PR) |
| 1 | `feat/fase-1-nucleo` | Entidade, embarque/desembarque, pilotagem, caldeira completa, vapor cegante, desgaste/reparo, HUD, recolher com chave, receitas/itens da Fase 1 |
| 2 | `feat/fase-2-modulos` | Cama, baús, fornalha, compactador, painel com abas |
| 3 | `feat/fase-3-montagem` | Blocos, gabarito, montagem/desmontagem |
| — | release | README final + GitHub Release com o `.jar` |
