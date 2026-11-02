/** The only configuration: the API base URL (inlined at build time). */
export const API_BASE_URL = (process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
