import type { Role, Session } from '@/api/adapters';

export const APP_ROLE: Role = 'STUDENT';

export class WrongRoleError extends Error {
  constructor(readonly role: Role) {
    super(
      role === 'VENDOR'
        ? 'This is the student app. Canteen staff should use the vendor dashboard.'
        : role === 'ADMIN'
          ? 'This is the student app. Admins should use the admin dashboard.'
          : 'This account cannot use the student app.',
    );
    this.name = 'WrongRoleError';
  }
}

/** Each app accepts only its own role; any other account is refused at login and on restore. */
export function assertAppRole(session: Session, expected: Role = APP_ROLE): Session {
  if (session.user.role !== expected) throw new WrongRoleError(session.user.role);
  return session;
}
