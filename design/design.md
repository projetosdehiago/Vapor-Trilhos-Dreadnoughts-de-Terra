# Vapor & Trilhos: Dreadnoughts de Terra — Documento de Design

> **Status:** v0.5 — Java: Fases 1 (núcleo), 2 (módulos) e 3 (montagem) implementadas (ver B5).
> Bedrock: Fase B1 (landship e caldeira) implementada (ver C5).
> Este documento é a fonte para a versão Java (Fabric) **e** para a versão Bedrock.
> Por isso ele é dividido em três partes:
>
> - **Parte A — Ideia (independente de plataforma):** conceito, regras, números e receitas.
>   Tudo aqui é expresso em blocos, segundos, °C, bar e mB, sem citar APIs.
> - **Parte B — Implementação Java:** como a Parte A vira código no Fabric 26.3.
> - **Parte C — Implementação Bedrock:** como a Parte A vira um add-on (pasta `bedrock/`).
>
> Se a Parte A mudar, as Partes B e C devem acompanhar. Elas nunca devem introduzir regra de
> jogo que não esteja na Parte A; o que muda por limite da plataforma fica anotado em C2.

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
| Plataforma | O teto do casco (2 blocos) é sólido: dá para subir e ficar em pé, mas não atravessar. Quem está em pé em cima anda e gira junto com o veículo |

### A2.0 Visual

Fonte do modelo: `design/model/` (geometria Bedrock, a mesma que o GeckoLib e o Bedrock usam).
Cabine aberta com teto de cobre na frente e lanterna, leme de navio em pé sobre um pedestal
virado para o piloto, bancos no meio, caldeira verde com cintas de cobre, manômetro e chaminé
atrás, dois cilindros verticais com pistões entre bancos e caldeira, faróis na frente. Esteiras
com saia blindada por fora cobrindo a metade de cima; as rodas de apoio aparecem embaixo. Os 6 encaixes de módulo ficam nas laterais do deque (células 3×3); o
compactador é um rolo à frente. Altura visual até o topo da chaminé: 3,4 blocos.

A fumaça da chaminé e o vapor contínuo da zona vermelha **não aparecem para quem está a bordo**
(tapavam a câmera em terceira pessoa); quem vê de fora continua vendo. As nuvens de vapor dos
eventos (válvula de segurança, alívio manual, choque térmico) aparecem para todos.

### A2.1 Controles (padrão; todos reconfiguráveis)

| Ação | Tecla padrão | Efeito |
|---|---|---|
| Acelerar / frear-ré | W / S | Ajusta a velocidade alvo; S com o veículo parado dá ré |
| Girar | A / D | Esteiras giram no lugar (pivot) ou fazem curva em movimento |
| Desembarcar | Shift | Padrão do jogo |
| Painel do landship | E (inventário) enquanto embarcado | Abre caldeira + módulos |
| Abafador | R | Cicla Fechado → Normal → Aberto |
| Válvula de alívio | V | Solta vapor manualmente (−2 bar) |
| Apito | H | Som apenas (diversão / sinalização multiplayer). O som é escolhido na aba **Apito** do painel: Apito a vapor (padrão), Buzina de nevoeiro, Sino de navio, Corneta de guerra ou **Personalizado: gemidão** (áudio próprio do mod). Cada landship guarda o seu; todos por perto ouvem |
| Compactador | C | Liga/desliga o compactador frontal |
| Desmontar | J (2 toques) | De fora, olhando para o landship: o 1º toque pede confirmação, o 2º (em até 3 s) desmonta em blocos (A8) |

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

> **Decisão:** as duas funções, e o ponto de renascimento só vale enquanto o landship existir.

- **Dormir:** com o veículo parado (velocidade 0) e regras normais de sono (noite/tempestade,
  sem monstros a 8 blocos), o jogador pode dormir na cama do landship (botão **Dormir** na aba
  Módulos do painel); conta para pular a noite. Ao deitar, o landship se alinha ao múltiplo de
  90° mais próximo (o corpo deitado só aparece nessas 4 direções; a pegada é quadrada, então o
  espaço ocupado não muda). Ao acordar, o jogador levanta ao lado do veículo.
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
de 3 blocos de largura logo à frente das esteiras, em duas fileiras (2 e 3 blocos à frente do
centro; a 5 m/s o veículo anda 1,25 bloco entre duas passadas, então nada escapa):

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
(e o conteúdo dos baús transferido). Se não estiver, uma mensagem diz o que falta e fumaça
marca os blocos errados.

**Desmontar** (botão "Desmontar em blocos" na aba Módulos do painel, ou a tecla Desmontar
apertada duas vezes olhando para o landship) faz o inverso: o landship
vira os blocos do gabarito no lugar onde está, virado para a frente dele. Exige o veículo
parado, sem ninguém a bordo, com a caldeira fria (fogo apagado, ≤ 60 °C) e o **casco 100 %
reparado** (senão desmontar e montar de novo seria um conserto de graça). Verifica o espaço de
todos os blocos antes de mexer em qualquer um; sem espaço, recusa. Os baús voltam como blocos
com o conteúdo; o combustível e a fornalha voltam para o jogador; a água do tanque se perde.
Agachar + chave continua recolhendo o landship como item (A2.1).

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

Limites do gabarito: até 4 baús, 1 cama e 1 fornalha (como em A6). Os blocos de peça também
servem sozinhos: o Baú de Carga como bloco funciona como um baú comum de 27 espaços.

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
  degrau de 1 bloco via `maxUpStep()`.
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
  regras de encaixe e limites; `ModuleSlot`: os 6 encaixes do deque e o da frente.
- `LandshipModules` guarda o que está instalado (e a ordem, para a chave tirar o último), um
  trecho de 27 espaços de carga por encaixe do deque, a fornalha e o estado do compactador. Os
  clientes recebem só um inteiro sincronizado (3 bits por encaixe + compactador ligado).
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

- Dormir: no 26.3 o sono vanilla (`startSleeping`) exige um bloco de cama. O botão do painel
  faz o que a cama vanilla faz (pose de sono, posição de sono, estatística, lista de quem dorme)
  e o `EntitySleepEvents.ALLOW_BED` (Fabric) confirma a cada tick que aquele ponto é cama
  enquanto o landship estiver parado ali. O resto é o fluxo normal: pular a noite, "Sair da
  cama", acordar de manhã. `MODIFY_SLEEPING_DIRECTION` dá a direção do corpo e `STOP_SLEEPING`
  levanta o jogador ao lado do veículo. Sem mixin.
- Renascimento: o "lar" (UUID do landship) é um **Data Attachment** persistente no jogador,
  copiado na morte. O índice landship → posição (dimensão, posição, direção) é outro anexo
  persistente, no Overworld (dispensa `SavedData`); o landship atualiza a própria entrada a cada
  segundo e a remove quando é destruído, recolhido ou perde a cama. Na morte
  (`ServerLivingEntityEvents.AFTER_DEATH`), o lugar ao lado do landship vira o ponto de
  renascimento **forçado** do jogo, e o ponto normal do jogador fica guardado num anexo; no
  `ServerPlayerEvents.AFTER_RESPAWN` ele volta. (Teleportar depois de renascer era instável: o
  cliente ainda está carregando o mundo.) Sem entrada no índice, o lar é apagado e vale o spawn
  normal. Dormir numa cama comum troca o lar
  (`ALLOW_SETTING_SPAWN`).

### B3.6 Compactador

Roda no servidor; para cada bloco: `level.mayInteract(piloto, pos)` + evento
`PlayerBlockBreakEvents.BEFORE` (mods de proteção podem vetar). Listas por **tags** de dados:
`vapor_trilhos:compactable`, `vapor_trilhos:flattens_to_path`, `vapor_trilhos:fill_material`,
`vapor_trilhos:compactor_immune` — configuráveis por datapack.

### B3.7 Fase 3 — gabarito

Blocos próprios (chassi, leme com `FACING`, esteira, caldeira, módulos como blocos; o baú com
`BlockEntity` de 27 espaços). Os itens que já existiam viraram `BlockItem` com o mesmo id e o
mesmo nome. `assembly/LandshipAssembly` checa o gabarito a partir do leme (rotacionado pela
direção dele), tira o conteúdo dos baús antes de remover os blocos (senão ele cairia no chão),
remove os blocos e cria a entidade num único tick do servidor. Desmontagem: verifica espaço
livre para todos os blocos antes de mexer em qualquer um.

### B3.8 Recursos

- **Data generation** (Fabric Data Generation API) para receitas, tags, loot tables, modelos e
  os dois arquivos de idioma (`en_us`, `pt_br`) — o CI garante que nada fica faltando.
  Na hora de montar o `.jar`, o `pt_br` é copiado como `pt_pt` (português de Portugal) e o
  `en_us` como `en_gb`, `en_au`, `en_ca` e `en_nz`: um texto só para manter por língua.
- **Modelo da entidade com GeckoLib 5** (decisão do usuário): `landship.geo.json` +
  `landship.animation.json` + `landship.png` + `landship_glowmask.png`, gerados por
  `design/model/gen_landship.py` e copiados para `assets/vapor_trilhos/geckolib/models/entity/`,
  `.../geckolib/animations/entity/` e `textures/entity/`. Todos abrem no Blockbench.
  - Ossos que o código controla: `slot_*_{bed,cargo,furnace}` e `slot_front_compactor`
    (visibilidade conforme os módulos instalados), `gauge_needle` (pressão), `helm` (direção),
    `body` (arfagem/rolagem do terreno).
  - Animações por controlador independente: `track_left.*` e `track_right.*` (cada esteira
    anda para frente/ré, então girar no lugar = uma esteira em cada sentido). Cada esteira é uma
    corrente de 21 elos (`tread_<lado>_<n>`) que contorna as rodas dentadas (`sprocket_*`) e passa
    sobre as rodas de apoio (`wheel_*`). Um ciclo (2,4 s) é uma volta inteira da roda dentada: a
    corrente avança 8 elos e tudo volta exatamente à mesma pose, então o laço não tem emenda.
    Velocidade 1× da animação ≈ 1 bloco/s; o código ajusta à velocidade real.
    Também: `engine.idle` /
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

## B5. Estado da implementação

### Fase 1 — núcleo (implementada)

| Parte da Parte A | Onde está |
|---|---|
| Caldeira (A3) | `boiler/BoilerSimulation` (Java puro, 13 testes JUnit) + `LandshipEntity.serverTick` |
| Movimento (A4) | `LandshipEntity.drive()` no lado com autoridade; terreno por tags `vapor_trilhos:terrain/*` |
| Integridade e reparo (A5) | `LandshipEntity` + `RepairMaterials` + tags `vapor_trilhos:repair/*` |
| Itens e receitas (A7) | `registry/ModItems`, `data/vapor_trilhos/recipe/` (Fase 1: esteira, caldeira, landship, chave, kit) |
| Painel, HUD, teclas | `client/screen/LandshipScreen`, `client/hud/LandshipHud`, `VaporTrilhosClient` |

Decisões e detalhes que a Parte A não fixava:

- **Combustível no 26.3:** o tempo de queima virou componente de dados (`minecraft:cooking_fuel`)
  resolvido com um contexto de loot que depende do bloco (fornalha × alto-forno). Usamos o
  contexto de uma fornalha comum (`FuelHelper`): carvão = 80 s, como na Parte A.
- **Água:** qualquer recipiente reconhecido pela Transfer API (balde, garrafa, mods). O
  tanque só aceita o recipiente inteiro (um balde não entra com menos de 1.000 mB livres).
- **Giro:** o giro também precisa de vapor — a velocidade de giro é proporcional à potência,
  com mínimo de 35 % enquanto houver pelo menos 2 bar.
- **Colisão:** o servidor detecta batidas pela desaceleração (queda de mais de 1,5 m/s num
  tick partindo de 3 m/s ou mais), porque com o piloto no comando quem move é o cliente.
- **Botões do painel** usam o mecanismo vanilla de botão de menu (`clickMenuButton`); as teclas
  R, V e H usam um payload próprio (`LandshipActionPayload`), validado no servidor (só o piloto).
- **Janela do jogo:** o 26.3 usa SDL3 em vez de GLFW. Em ambiente sem tela (CI/Xvfb), o cliente
  precisa de `SDL_VIDEO_FORCE_EGL=1`.
- **Chaminé:** o modelo fica como está; a fumaça e o vapor contínuos são escondidos só para o
  jogador local quando ele está a bordo (o cliente informa isso à entidade por um predicado
  registrado em `VaporTrilhosClient`).
- **Plataforma (pedido do amigo, "como o ghast feliz parado, mas andando como o Create"):** a
  caixa de colisão de 2,9 × 2,0 já era sólida; agora, a cada tick, quem está em pé no teto é
  movido pelo mesmo deslocamento e giro do landship (`carryEntitiesOnTop`). Cada lado move só o
  que controla: o cliente move o próprio jogador, o servidor move mobs e itens; sem mixin. O
  landship ignora quem está em cima ao calcular a própria colisão, para não travar ao subir
  degraus. Limite: a colisão é uma caixa só, então quem sobe fica na altura do teto da cabine
  (2 blocos), não no deque; caixas separadas para deque, cabine e caldeira exigiriam mixins.
- **Esteiras para quem vê de fora:** o deslocamento usado na animação agora vem da posição do
  tick anterior gravada antes da interpolação, então as esteiras também giram para os outros
  jogadores (antes só giravam para o piloto).

Testes da Fase 1: 13 JUnit (caldeira), 17 GameTests de servidor (`./gradlew build`), 1 GameTest
de cliente (`./gradlew runClientGameTest`, no CI com Xvfb e prints como artefato).

### Fase 2 — módulos (implementada)

| Parte da Parte A | Onde está |
|---|---|
| Encaixes, limites, instalar/remover (A6) | `module/ModuleType`, `ModuleSlot`, `LandshipModules`; `LandshipEntity.tryInstallModule/tryRemoveModule` |
| Massa dos módulos (A4) | `LandshipEntity.drive()`: −3 % de velocidade máxima por módulo |
| Cama (A6.1) | `module/LandshipBed` + `registry/ModAttachments` |
| Baú de carga (A6.2) | aba Carga do `LandshipMenu` (uma página por baú) |
| Fornalha (A6.3) | `module/FurnaceModule` (receitas de fundição comuns, 5 s por item) |
| Compactador (A6.4) | `module/Compactor` + tags `vapor_trilhos:compactor/*` |
| Painel com abas | `LandshipMenu` / `LandshipScreen` |

Decisões e detalhes:

- **Painel:** todos os espaços existem sempre (combustível, 6 × 27 de carga, fornalha); a aba e a
  página escolhidas decidem quais ficam ativos. O duplo clique não junta itens de baús
  escondidos. Alcance: a bordo ou a até 5 blocos do casco.
- **Fornalha:** cada tick trabalhando gasta 1 tick a mais de queima da caldeira (5 s de queima
  por item) e tira 0,05 bar/s da geração de vapor. A experiência fica guardada e sai quando
  alguém retira a saída. Com a caldeira abaixo de 100 °C ou o fogo apagado, o progresso para.
- **Compactador:** age em nome do piloto (`mayInteract` + `PlayerBlockBreakEvents.BEFORE`, então
  mods de proteção podem vetar); guarda os drops e tira o aterro dos baús pela Transfer API
  (`ContainerStorage` + `CombinedStorage`, só os trechos dos baús instalados).
- **Destruição:** os módulos e tudo o que estava nos baús e na fornalha caem no chão.
- **Recolher:** com módulos instalados, a chave tira o último módulo; só recolhe sem nenhum.

Testes: 14 JUnit, 25 GameTests de servidor (8 dos módulos) e 2 GameTests de cliente (o segundo
instala todos os módulos, fotografa as abas, dorme até de manhã e renasce ao lado do landship).

### Apito com escolha de som

`landship/WhistleSound` lista as 5 opções; a escolhida fica num dado sincronizado da entidade e é
salva com ela. Os 4 primeiros sons são **originais, sintetizados por código** em
`design/audio/gen_whistles.py` (numpy + ffmpeg; síntese aditiva, ruído filtrado e reverberação
de Schroeder), então não há licença de terceiros a respeitar:

| Som | Como é feito |
|---|---|
| Apito a vapor | 3 tubos em acorde de fá# menor, com a "subida" de tom ao abrir e o chiado do vapor |
| Buzina de nevoeiro | tom grave (104 Hz) e áspero que termina num "grunhido" descendo |
| Sino de navio | duas batidas, parciais desafinados de sino e decaimento longo |
| Corneta de guerra | chamada de duas notas (sol, ré) com brilho de metal no ataque e vibrato |

O personalizado (`gemidao.ogg`) é o áudio enviado pelo usuário, convertido para OGG Vorbis
(`ffmpeg -i entrada.mp3 -ac 1 -ar 44100 -c:a libvorbis -q:a 4 gemidao.ogg`). Todos são **mono**:
o jogo só diminui com a distância os sons mono. A recarga de cada opção acompanha a duração do
som (o personalizado tem 7 s).

### Fase 3 — montagem (implementada)

| Parte da Parte A | Onde está |
|---|---|
| Blocos e receitas (A7) | `registry/ModBlocks`, `ModBlockEntities`, `assembly/LandshipHelmBlock`, `assembly/CargoModuleBlock` |
| Gabarito, montar, desmontar (A8) | `assembly/LandshipAssembly` (`Layout`, `scan`, `assemble`, `disassemble`) |
| Botão Desmontar | `LandshipMenu.BUTTON_DISASSEMBLE` / aba Módulos da `LandshipScreen` |
| Tecla Desmontar (J) | `VaporTrilhosClient.DISASSEMBLE` → `network/LandshipDisassemblePayload` |

Decisões e detalhes:

- **Desmontar pelo painel ou pela tecla J**, e não pela chave: Shift + chave já recolhe o
  landship como item, e as duas coisas continuam existindo.
- **Tecla J com confirmação:** o primeiro toque só avisa ("aperte J de novo"); o segundo, em
  até 3 s e no mesmo landship, manda um payload com o id da entidade. Quem desmonta está do
  lado de fora (o veículo precisa estar vazio), então o alvo vem da mira, não do veículo. O
  servidor confere o alcance do painel (`isUsableBy`) e as mesmas regras do botão.
- **Leme:** o modelo usa a base `orientable` girada 180°: o volante fica virado para quem está
  atrás do leme (o piloto) e a seta no topo aponta para a frente do landship.
- **Texturas dos blocos:** 32×32, geradas por `design/textures/gen_block_textures.py` com o mesmo
  kit dos itens (faces cheias, que repetem lado a lado).
- **Texturas dos itens:** 32×32, geradas por `design/textures/gen_item_textures.py` com o kit
  `pixelkit.py`; as mesmas imagens vão para o pacote de recursos do Bedrock.

Testes: 14 JUnit, 34 GameTests de servidor (9 da montagem: as 4 direções, módulos e baús,
gabarito incompleto, baús demais, ida e volta, desmontagem recusada) e 4 GameTests de cliente
(o terceiro monta com um clique real da chave no leme, desmonta pelo painel, monta de novo e
desmonta pela tecla J, conferindo que um toque só não desmonta; o quarto abre a aba do mod no
criativo, tira um print e confere que as traduções copiadas chegaram ao jogo).

## B4. Plano de entregas

| Fase | PR | Entrega |
|---|---|---|
| 0 | `chore/scaffold` | Projeto Gradle/Loom, `fabric.mod.json`, `.gitignore`, LICENSE MIT, README, GitHub Actions (build + testes em push/PR) |
| 1 | `feat/fase-1-nucleo` | Entidade, embarque/desembarque, pilotagem, caldeira completa, vapor cegante, desgaste/reparo, HUD, recolher com chave, receitas/itens da Fase 1 |
| 2 | `feat/fase-2-modulos` | Cama, baús, fornalha, compactador, painel com abas |
| 3 | `feat/fase-3-montagem` | Blocos, gabarito, montagem/desmontagem |
| — | release | README final + GitHub Release com o `.jar` |

---

# PARTE C — IMPLEMENTAÇÃO BEDROCK (add-on)

## C1. Versões e ferramentas (pesquisa em 2026-10-09)

| Item | Versão |
|---|---|
| Minecraft Bedrock | 26.50 (motor 1.26.50) — `min_engine_version` dos manifestos |
| `@minecraft/server` | 2.10.0 (estável) |
| `@minecraft/server-ui` | 2.2.0 (estável; formulários com dados ao vivo, "DDUI") |
| Linguagem | TypeScript, empacotado com esbuild num único `scripts/main.js` |
| Testes | Vitest (lógica pura: caldeira e direção) + `scripts/check.mjs` (referências entre arquivos) |

Só APIs **estáveis**: nada de "Beta APIs" nem "Recursos experimentais", para funcionar em
qualquer mundo, servidor e Realm. Plataformas-alvo: PC (Windows) e celular/tablet.

Estrutura de `bedrock/`:

| Caminho | Conteúdo |
|---|---|
| `src/boiler/` | Tradução direta de `BoilerSimulation`/`BalanceConstants`/`Damper`/`BoilerState` |
| `src/landship/drive.ts` | Direção (A4) sem depender do jogo, testável |
| `src/landship/landship.ts` | Estado por landship, tick, movimento, desgaste, efeitos |
| `src/landship/{interact,item,panel,hud,whistle}.ts` | Interação, colocar/recolher, painel, medidores, apitos |
| `packs/BP`, `packs/RP` | Pacotes de comportamento e de recursos (só o que é exclusivo do Bedrock) |
| `scripts/build.mjs` | Compila, copia os recursos compartilhados e gera `dist/vapor-trilhos-bedrock-<versão>.mcaddon` |
| `scripts/check.mjs` | Confere manifestos, traduções, ícones, sons, partículas, animações e peças do modelo |

Recursos compartilhados com o Java (copiados pelo build, nunca duplicados no repositório):
modelo e animações de `design/model/`, texturas dos itens e sons dos apitos dos assets do mod.
Idiomas: `pt_BR` e `en_US`; o build copia o primeiro como `pt_PT` e o segundo como `en_GB`.

## C2. Adaptações por limite da plataforma

| Parte A | Limite do Bedrock | Como ficou |
|---|---|---|
| Teclas R, V, H (A2.1) | Add-ons não registram teclas | Item **Painel de Comando** (` G ` / `CRC`: vidro, cobre, redstone): usar abre o painel com Abafador, Válvula de alívio, Apito e Acender/Apagar. **Pular enquanto pilota = apito.** Funciona igual no celular |
| Painel com espaços de itens | O formulário de script não mostra espaços | Painel = formulário com medidores ao vivo e botões; o combustível fica no **inventário do veículo** (abrir o inventário a bordo), 5 espaços em vez de 3 |
| HUD com medidores | Sem HUD personalizada | Linha na barra de ação para quem está a bordo (pressão com barra colorida, temperatura, água, fogo, casco), a cada 5 ticks |
| Direção A/D gira no lugar | O "rideable" padrão segue o olhar do piloto | O script lê o WASD do piloto (`inputInfo.getMovementVector()`) e aplica a mesma conta do Java (`drive.ts`) |
| Teto que carrega quem está em cima | Entidade sólida não carrega jogadores | O script empurra quem está no teto pelo mesmo deslocamento/giro do casco (`applyKnockback`), pode ser menos suave que no Java |
| Fumaça invisível para quem está a bordo | — | Partículas mandadas só para quem não está no veículo (`player.spawnParticle`) |
| Garrafa d'água | O Bedrock não diz se a poção é água de forma garantida | Reconhece a garrafa pelo tipo da poção; o balde sempre funciona |
| Tempo de queima | Não é exposto para scripts | Tabela da fornalha comum (`game/items.ts`) |

## C3. Desenho técnico

- **Entidade** `vapor_trilhos:landship`: caixa de colisão 2,9 × 2 (sólida, dá para ficar em cima),
  física do jogo (gravidade e colisão), 3 assentos (o primeiro é o do piloto), inventário de 5
  espaços para combustível, imune a fogo/queda/afogamento (o script trata queda e lava pela A5).
- **Movimento:** a cada tick, `step()` (a mesma conta do Java) dá a velocidade e o giro; o
  script gira a entidade e troca a velocidade horizontal (`clearVelocity` + `applyImpulse`),
  mantendo a vertical para a gravidade. Se a frente bloqueia e há espaço, sobe 1 bloco (degrau).
- **Integridade:** guardada pelo script. A "vida" da entidade é só um amortecedor enorme: o dano
  do jogo (ataques ×1, explosões ×2) vira desgaste e a vida volta ao máximo.
- **Dados salvos** em propriedades dinâmicas: caldeira (JSON compacto), integridade e apito. O
  item Landship guarda integridade e água quando recolhido.
- **Animações:** propriedades da entidade (`track_left`, `track_right`, `working`, `venting`)
  ligam os controles de animação do pacote de recursos; os encaixes de módulo ficam ocultos.
- **Partículas próprias** (`steam_burst`, `steam_puff`, `chimney_smoke`, `black_smoke`) com a
  textura de fumaça do jogo.

## C4. Testes

- `npm test`: os mesmos casos de `BoilerSimulationTest.java` + direção (aceleração, ré, giro,
  terreno, conversão do analógico).
- `npm run check`: referências entre arquivos do add-on montado.
- **No jogo** (não dá para rodar o Bedrock no CI): roteiro de teste em cada PR.

## C5. Estado

| Fase | Entrega | Estado |
|---|---|---|
| B1 | Landship, caldeira, direção, vapor cegante, desgaste/reparo, HUD, painel, apitos, colocar/recolher, receitas | implementada (aguardando teste no jogo) |
| B2 | Módulos (cama por script, baús, fornalha, compactador) | a fazer |
| B3 | Montagem com blocos | a fazer |
