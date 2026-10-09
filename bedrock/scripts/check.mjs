// Confere o add-on montado em dist/ (rode depois de "npm run build"). Sem o jogo para testar
// aqui, isto pega o que mais quebra um add-on em silêncio: referências erradas entre arquivos.
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const BP = join(ROOT, "dist/vapor_trilhos_BP");
const RP = join(ROOT, "dist/vapor_trilhos_RP");
const errors = [];
const fail = (msg) => errors.push(msg);

if (!existsSync(BP) || !existsSync(RP)) {
  console.error("Rode npm run build antes.");
  process.exit(1);
}

function files(dir, ext) {
  const out = [];
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) {
      out.push(...files(path, ext));
    } else if (path.endsWith(ext)) {
      out.push(path);
    }
  }
  return out;
}

const json = {};
for (const file of [...files(BP, ".json"), ...files(RP, ".json")]) {
  try {
    json[file] = JSON.parse(readFileSync(file, "utf8"));
  } catch (e) {
    fail(`JSON inválido: ${file}: ${e.message}`);
  }
}
const read = (dir, path) => json[join(dir, path)];
const source = files(join(ROOT, "src"), ".ts").map((f) => readFileSync(f, "utf8")).join("\n");

// manifestos apontam um para o outro
const bpManifest = read(BP, "manifest.json");
const rpManifest = read(RP, "manifest.json");
if (!bpManifest.dependencies.some((d) => d.uuid === rpManifest.header.uuid)) fail("BP não depende do RP");
if (!rpManifest.dependencies.some((d) => d.uuid === bpManifest.header.uuid)) fail("RP não depende do BP");
const uuids = [bpManifest.header, ...bpManifest.modules, rpManifest.header, ...rpManifest.modules].map((m) => m.uuid);
if (new Set(uuids).size !== uuids.length) fail("UUIDs repetidos nos manifestos");

// textos: as duas línguas com as mesmas chaves, e todas as chaves usadas existem
function lang(path) {
  const map = new Map();
  for (const line of readFileSync(path, "utf8").split("\n")) {
    if (line.trim() && !line.startsWith("##")) {
      const i = line.indexOf("=");
      map.set(line.slice(0, i), line.slice(i + 1));
    }
  }
  return map;
}
const langs = { en_US: lang(join(RP, "texts/en_US.lang")), pt_BR: lang(join(RP, "texts/pt_BR.lang")) };
for (const key of langs.en_US.keys()) if (!langs.pt_BR.has(key)) fail(`falta em pt_BR: ${key}`);
for (const key of langs.pt_BR.keys()) if (!langs.en_US.has(key)) fail(`falta em en_US: ${key}`);
for (const [key, value] of langs.pt_BR) {
  const count = (v) => (v.match(/%s/g) ?? []).length;
  if (count(value) !== count(langs.en_US.get(key) ?? "")) fail(`número de %s diferente entre as línguas: ${key}`);
}
for (const dir of [BP, RP]) {
  for (const code of JSON.parse(readFileSync(join(dir, "texts/languages.json"), "utf8"))) {
    if (!existsSync(join(dir, `texts/${code}.lang`))) fail(`idioma ${code} listado sem arquivo em ${dir}`);
  }
}
const used = new Set(source.match(/"vapor_trilhos\.[a-z_.]+"/g)?.map((s) => s.slice(1, -1)) ?? []);
for (const key of used) if (!langs.pt_BR.has(key)) fail(`texto usado no código e sem tradução: ${key}`);
for (const prefix of ["damper", "whistle"]) {
  const keys = { damper: ["closed", "normal", "open"], whistle: ["steam", "foghorn", "bell", "war_horn", "custom"] }[prefix];
  for (const k of keys) if (!langs.pt_BR.has(`vapor_trilhos.${prefix}.${k}`)) fail(`sem tradução: vapor_trilhos.${prefix}.${k}`);
}

// itens: ícone, nome, componentes de script
const atlas = read(RP, "textures/item_texture.json").texture_data;
for (const file of files(join(BP, "items"), ".json")) {
  const item = json[file]["minecraft:item"];
  const c = item.components;
  const icon = c["minecraft:icon"];
  if (!atlas[icon]) fail(`${item.description.identifier}: ícone ${icon} fora do item_texture.json`);
  else if (!existsSync(join(RP, `${atlas[icon].textures}.png`))) fail(`textura ausente: ${atlas[icon].textures}.png`);
  if (!langs.pt_BR.has(c["minecraft:display_name"].value)) fail(`sem nome traduzido: ${item.description.identifier}`);
  for (const key of Object.keys(c).filter((k) => k.startsWith("vapor_trilhos:"))) {
    if (!source.includes(`\`\${NS}:${key.split(":")[1]}\``)) fail(`componente ${key} não registrado no script`);
  }
}
for (const file of files(join(BP, "recipes"), ".json")) {
  const recipe = Object.values(json[file]).find((v) => typeof v === "object");
  const result = recipe.result.item;
  if (result.startsWith("vapor_trilhos:") && !existsSync(join(BP, `items/${result.split(":")[1]}.json`))) fail(`receita de item inexistente: ${result}`);
}

// entidade: propriedades usadas pelo script e pelas animações existem; peças escondidas existem no modelo
const entity = read(BP, "entities/landship.json")["minecraft:entity"];
const props = Object.keys(entity.description.properties);
for (const p of source.match(/`\$\{NS\}:(track_left|track_right|working|venting|[a-z_]+)`/g) ?? []) {
  const id = p.replace("`${NS}:", "vapor_trilhos:").replace("`", "");
  if (/track_|working|venting/.test(id) && !props.includes(id)) fail(`propriedade ${id} não declarada na entidade`);
}
const client = read(RP, "entity/landship.entity.json")["minecraft:client_entity"].description;
const geo = read(RP, "models/entity/vapor_trilhos/landship.geo.json")["minecraft:geometry"][0];
if (geo.description.identifier !== client.geometry.default) fail("identificador da geometria diferente");
if (!existsSync(join(RP, `${client.textures.default}.png`))) fail("textura do landship ausente");
const animations = read(RP, "animations/vapor_trilhos/landship.animation.json").animations;
const controllers = read(RP, "animation_controllers/landship.animation_controllers.json").animation_controllers;
for (const [short, full] of Object.entries(client.animations)) {
  if (!animations[full] && !controllers[full]) fail(`animação ${short} → ${full} não existe`);
}
const controllerText = JSON.stringify(controllers);
for (const id of controllerText.match(/vapor_trilhos:[a-z_]+/g) ?? []) if (!props.includes(id)) fail(`controle de animação usa ${id}, que não existe`);
const bones = new Set(geo.bones.map((b) => b.name));
const visibility = read(RP, "render_controllers/landship.render_controllers.json").render_controllers["controller.render.vapor_trilhos.landship"].part_visibility;
for (const entry of visibility) for (const bone of Object.keys(entry)) if (bone !== "*" && !bones.has(bone)) fail(`peça ${bone} não existe no modelo`);

// sons e partículas usados pelo script
const sounds = read(RP, "sounds/sound_definitions.json").sound_definitions;
for (const [id, def] of Object.entries(sounds)) {
  for (const s of def.sounds) if (!existsSync(join(RP, `${s.name}.ogg`))) fail(`som ${id}: arquivo ${s.name}.ogg ausente`);
}
for (const id of source.match(/"vapor_trilhos\.whistle\.[a-z_]+"/g) ?? []) {
  const key = id.slice(1, -1);
  if (/^vapor_trilhos\.whistle\.[a-z_]+$/.test(key) && source.includes(`sound: ${id}`) && !sounds[key]) fail(`som ${key} não definido`);
}
const particles = new Set(files(join(RP, "particles"), ".json").map((f) => json[f].particle_effect.description.identifier));
for (const m of source.match(/`\$\{NS\}:(steam_burst|steam_puff|chimney_smoke|black_smoke)`/g) ?? []) {
  const id = m.replace("`${NS}:", "vapor_trilhos:").replace("`", "");
  if (!particles.has(id)) fail(`partícula ${id} não existe`);
}

if (errors.length) {
  console.error(errors.map((e) => `✗ ${e}`).join("\n"));
  process.exit(1);
}
console.log("add-on conferido: referências entre arquivos ok");
