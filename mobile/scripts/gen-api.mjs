// Generates src/api/schema.d.ts from the backend's OpenAPI document.
// openapi-typescript runs through npx (it declares a TypeScript 5 peer, this app uses TypeScript 6).
import { spawnSync } from 'node:child_process';

const base = (process.env.EXPO_PUBLIC_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const result = spawnSync(
  'npx',
  ['--yes', 'openapi-typescript@7.13.0', `${base}/v3/api-docs`, '-o', 'src/api/schema.d.ts'],
  { stdio: 'inherit', shell: process.platform === 'win32' },
);
process.exit(result.status ?? 1);
