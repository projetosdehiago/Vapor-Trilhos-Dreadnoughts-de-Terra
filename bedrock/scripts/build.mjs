// Monta o add-on: compila o script, junta os pacotes com os recursos compartilhados com a
// versão Java (modelo, texturas, sons) e gera o .mcaddon em dist/.
import { build } from "esbuild";
import { cpSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { crc32, deflateRawSync } from "node:zlib";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const REPO = join(ROOT, "..");
const JAVA_ASSETS = join(REPO, "src/main/resources/assets/vapor_trilhos");
const DIST = join(ROOT, "dist");
const BP = join(DIST, "vapor_trilhos_BP");
const RP = join(DIST, "vapor_trilhos_RP");
const { version } = JSON.parse(readFileSync(join(ROOT, "package.json"), "utf8"));
const semver = version.split(".").map(Number);

rmSync(DIST, { recursive: true, force: true });
cpSync(join(ROOT, "packs/BP"), BP, { recursive: true });
cpSync(join(ROOT, "packs/RP"), RP, { recursive: true });

await build({
  entryPoints: [join(ROOT, "src/main.ts")],
  outfile: join(BP, "scripts/main.js"),
  bundle: true,
  format: "esm",
  target: "es2022",
  external: ["@minecraft/server", "@minecraft/server-ui"],
  legalComments: "none",
  logLevel: "warning",
});

// recursos compartilhados: a mesma fonte da versão Java (design/ e os assets do mod)
const shared = [
  ["design/model/landship.geo.json", "models/entity/vapor_trilhos/landship.geo.json"],
  ["design/model/landship.animation.json", "animations/vapor_trilhos/landship.animation.json"],
  ["design/model/landship.png", "textures/entity/vapor_trilhos/landship.png"],
];
for (const item of ["landship", "reinforced_track", "steam_boiler", "repair_kit", "boilermaker_wrench"]) {
  shared.push([relative(REPO, join(JAVA_ASSETS, `textures/item/${item}.png`)), `textures/items/vapor_trilhos/${item}.png`]);
}
for (const sound of readdirSync(join(JAVA_ASSETS, "sounds/whistle"))) {
  shared.push([relative(REPO, join(JAVA_ASSETS, "sounds/whistle", sound)), `sounds/vapor_trilhos/whistle/${sound}`]);
}
for (const [from, to] of shared) {
  mkdirSync(dirname(join(RP, to)), { recursive: true });
  cpSync(join(REPO, from), join(RP, to));
}
cpSync(join(JAVA_ASSETS, "icon.png"), join(RP, "pack_icon.png"));
cpSync(join(JAVA_ASSETS, "icon.png"), join(BP, "pack_icon.png"));

// versão do package.json nos dois manifestos (e nas dependências entre eles)
for (const dir of [BP, RP]) {
  const file = join(dir, "manifest.json");
  const manifest = JSON.parse(readFileSync(file, "utf8"));
  manifest.header.version = semver;
  for (const module of manifest.modules) {
    module.version = semver;
  }
  for (const dep of manifest.dependencies) {
    if (dep.uuid) {
      dep.version = semver;
    }
  }
  writeFileSync(file, `${JSON.stringify(manifest, null, 2)}\n`);
}

const addon = join(DIST, `vapor-trilhos-bedrock-${version}.mcaddon`);
writeZip(addon, [BP, RP]);
console.log(`${relative(REPO, addon)} (${(statSync(addon).size / 1024).toFixed(0)} KiB)`);

/** .mcaddon é um zip com as duas pastas de pacote. Escritor mínimo, sem dependências. */
function writeZip(target, folders) {
  const entries = [];
  const walk = (dir) => {
    for (const name of readdirSync(dir).sort()) {
      const path = join(dir, name);
      if (statSync(path).isDirectory()) {
        walk(path);
      } else {
        entries.push(path);
      }
    }
  };
  folders.forEach(walk);
  const chunks = [];
  const central = [];
  let offset = 0;
  for (const path of entries) {
    const name = Buffer.from(relative(DIST, path).split("\\").join("/"));
    const data = readFileSync(path);
    const packed = deflateRawSync(data, { level: 9 });
    const crc = crc32(data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6); // nomes em UTF-8
    local.writeUInt16LE(8, 8); // deflate
    local.writeUInt32LE(0x00210000, 10); // data fixa (1980-01-01): o zip sai igual a cada build
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(packed.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(name.length, 26);
    local.writeUInt16LE(0, 28);
    const header = Buffer.alloc(46);
    header.writeUInt32LE(0x02014b50, 0);
    header.writeUInt16LE(20, 4);
    header.writeUInt16LE(20, 6);
    header.writeUInt16LE(0x0800, 8);
    header.writeUInt16LE(8, 10);
    header.writeUInt32LE(0x00210000, 12);
    header.writeUInt32LE(crc, 16);
    header.writeUInt32LE(packed.length, 20);
    header.writeUInt32LE(data.length, 24);
    header.writeUInt16LE(name.length, 28);
    header.writeUInt32LE(offset, 42);
    chunks.push(local, name, packed);
    central.push(header, name);
    offset += local.length + name.length + packed.length;
  }
  const centralSize = central.reduce((n, b) => n + b.length, 0);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(entries.length, 8);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(centralSize, 12);
  end.writeUInt32LE(offset, 16);
  writeFileSync(target, Buffer.concat([...chunks, ...central, end]));
}
