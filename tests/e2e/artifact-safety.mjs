import { readdir, readFile } from 'node:fs/promises';
import { inflateRawSync } from 'node:zlib';

// Scan archive members too: plaintext searching a compressed trace alone misses credentials.
function zipMembers(bytes) {
  const members = [];
  const end = bytes.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]));
  if (end < 0) throw new Error('Invalid artifact archive');
  const count = bytes.readUInt16LE(end + 10); let offset = bytes.readUInt32LE(end + 16);
  for (let index = 0; index < count; index++) {
    if (bytes.readUInt32LE(offset) !== 0x02014b50) throw new Error('Invalid artifact archive directory');
    const method = bytes.readUInt16LE(offset + 10); const size = bytes.readUInt32LE(offset + 20);
    const local = bytes.readUInt32LE(offset + 42);
    const start = local + 30 + bytes.readUInt16LE(local + 26) + bytes.readUInt16LE(local + 28);
    const compressed = bytes.subarray(start, start + size);
    if (method === 0) members.push(compressed);
    else if (method === 8) members.push(inflateRawSync(compressed, { maxOutputLength: 100 * 1024 * 1024 }));
    else throw new Error('Unsupported artifact archive compression');
    offset += 46 + bytes.readUInt16LE(offset + 28) + bytes.readUInt16LE(offset + 30) + bytes.readUInt16LE(offset + 32);
  }
  return members;
}

export async function assertArtifactsSafe(directory, env) {
  const secrets = ['MYSQL_PASSWORD', 'MYSQL_ROOT_PASSWORD', 'INTERNAL_API_TOKEN']
    .map(key => env[key]).filter(value => value?.length >= 16).map(value => Buffer.from(value));
  if (secrets.length !== 3) throw new Error('Artifact audit requires the isolated project credentials');
  let checked = 0;
  async function visit(url) {
    for (const entry of await readdir(url, { withFileTypes: true })) {
      const path = new URL(encodeURIComponent(entry.name) + (entry.isDirectory() ? '/' : ''), url);
      if (entry.isDirectory()) await visit(path);
      else if (entry.isFile()) {
        const bytes = await readFile(path);
        const candidates = entry.name.endsWith('.zip') ? [bytes, ...zipMembers(bytes)] : [bytes];
        if (candidates.some(candidate => secrets.some(secret => candidate.includes(secret))))
          throw new Error('Sensitive value found in E2E artifacts; do not publish these artifacts');
        checked++;
      }
    }
  }
  await visit(directory);
  console.log(`Artifact safety: ${checked} files checked, including compressed trace members; no project credentials found.`);
}
