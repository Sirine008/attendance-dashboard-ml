/** Accès au stockage navigateur tolérant aux erreurs (navigation privée, stockage bloqué). */
export const safeStorage = {
  get(key: string, session = false): string | null {
    try {
      return (session ? sessionStorage : localStorage).getItem(key);
    } catch {
      return null;
    }
  },
  set(key: string, value: string, session = false): void {
    try {
      (session ? sessionStorage : localStorage).setItem(key, value);
    } catch {
      /* stockage indisponible : on continue sans persistance */
    }
  },
  remove(key: string, session = false): void {
    try {
      (session ? sessionStorage : localStorage).removeItem(key);
    } catch {
      /* idem */
    }
  },
};
