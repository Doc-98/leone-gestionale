import { afterEach, describe, expect, it, vi } from 'vitest';
import { cambiaServer, cercaAggiornamento, infoProgramma, installaAggiornamento, nelProgramma } from './dispositivi';

/** Nel browser queste funzioni non fanno niente; nel programma per Windows chiamano i comandi di Tauri. */
function nelProgrammaFinto(risposte: Record<string, unknown> = {}) {
  const invoke = vi.fn(async (comando: string) => {
    const r = risposte[comando];
    if (r instanceof Error) throw r;
    return r;
  });
  vi.stubGlobal('window', { __TAURI__: { core: { invoke } } });
  return invoke;
}

afterEach(() => vi.unstubAllGlobals());

describe('nel browser', () => {
  it('non è il programma e non chiama niente', async () => {
    vi.stubGlobal('window', {});
    expect(nelProgramma()).toBe(false);
    expect(await infoProgramma()).toBeNull();
    expect(await cercaAggiornamento()).toBeNull();
    await expect(cambiaServer()).resolves.toBeUndefined();
    await expect(installaAggiornamento()).resolves.toBeUndefined();
  });
});

describe('nel programma per Windows', () => {
  it('chiede versione, aggiornamenti e i comandi del programma', async () => {
    const invoke = nelProgrammaFinto({
      informazioni: { versione: '0.2.4', sistema: 'windows' },
      cerca_aggiornamento: { versione: '0.2.5', attuale: '0.2.4', note: null },
    });
    expect(nelProgramma()).toBe(true);
    expect(await infoProgramma()).toEqual({ versione: '0.2.4', sistema: 'windows' });
    expect(await cercaAggiornamento()).toMatchObject({ versione: '0.2.5' });
    await cambiaServer();
    await installaAggiornamento();
    expect(invoke.mock.calls.map((c) => c[0])).toEqual(['informazioni', 'cerca_aggiornamento', 'torna_all_avvio', 'installa_aggiornamento']);
  });

  it('se il programma non sa dire la versione, la pagina di accesso va avanti senza', async () => {
    nelProgrammaFinto({ informazioni: new Error('comando sconosciuto') });
    expect(await infoProgramma()).toBeNull();
  });
});
