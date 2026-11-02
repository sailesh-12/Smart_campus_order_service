// Generates src/api/schema.d.ts from the backend's OpenAPI document.
// Uses the locally installed openapi-typescript.
import { spawnSync } from 'node:child_process';

const base = (process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const result = spawnSync(
  'npx',
  ['openapi-typescript', `${base}/v3/api-docs`, '-o', 'src/api/schema.d.ts'],
  { stdio: 'inherit', shell: process.platform === 'win32' },
);
process.exit(result.status ?? 1);
