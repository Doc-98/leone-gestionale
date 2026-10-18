/**
 * Ciò che dipende da dove gira l'interfaccia: nel browser/PWA o nel programma per Windows (Tauri).
 * Il resto dell'app usa solo queste funzioni (ADR 0006, regola 3): un domani lettore NFC e stampa
 * arriveranno qui, con un'implementazione per Tauri e una per il web (Web NFC, stampa del browser).
 */

interface TauriGlobale {
  core: { invoke: <T>(comando: string, argomenti?: Record<string, unknown>) => Promise<T> };
}

function tauri(): TauriGlobale | undefined {
  return (window as unknown as { __TAURI__?: TauriGlobale }).__TAURI__;
}

/** Vero dentro il programma per Windows. */
export function nelProgramma(): boolean {
  return !!tauri();
}

export interface InfoProgramma {
  versione: string;
  sistema: string;
}

export async function infoProgramma(): Promise<InfoProgramma | null> {
  const t = tauri();
  if (!t) return null;
  try {
    return await t.core.invoke<InfoProgramma>('informazioni');
  } catch {
    return null;
  }
}

/** Nel programma: torna alla pagina iniziale per scegliere un altro server. */
export async function cambiaServer(): Promise<void> {
  await tauri()?.core.invoke('torna_all_avvio');
}
