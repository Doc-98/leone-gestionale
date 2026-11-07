import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * Il telefono come lettore del PC (ADR 0017): l'abbinamento, i codici che arrivano alla pagina in ascolto, lo stato
 * «collegato», l'abbinamento scaduto e la ripresa dopo aver ricaricato la pagina. Server, tempo e memoria della scheda
 * sono finti.
 */
const server = vi.hoisted(() => ({
  abbinaLettore: vi.fn(),
  lettureLettore: vi.fn(),
  scollegaLettore: vi.fn(),
}));

vi.mock('./api', async (originale) => {
  const vero = await originale<typeof import('./api')>();
  return { ...vero, api: server };
});

const { ErroreApi } = await import('./api');
// la funzione pura si importa una volta; il lettore invece si ricrea a ogni test (vedi nuovoLettore)
const { indirizzoPerTelefono } = await import('./lettore.svelte');

const memoria = new Map<string, string>();
const ABBINAMENTO = { id: 'abb-1', token: 'tok', codice: '123456' };

/** Una lettura del server: il telefono si è fatto vivo adesso, con questi codici. */
const letture = (...codici: [number, string][]) => ({
  telefonoVistoIl: new Date().toISOString(),
  letture: codici.map(([numero, codice]) => ({ numero, codice })),
});

async function nuovoLettore() {
  vi.resetModules();
  const m = await import('./lettore.svelte');
  return m.lettoreTelefono;
}

/** Fa passare il tempo e lascia finire le promesse del giro in corso. */
async function avanza(ms: number) {
  await vi.advanceTimersByTimeAsync(ms);
}

beforeEach(() => {
  vi.useFakeTimers();
  memoria.clear();
  vi.stubGlobal('sessionStorage', {
    getItem: (k: string) => memoria.get(k) ?? null,
    setItem: (k: string, v: string) => memoria.set(k, v),
    removeItem: (k: string) => memoria.delete(k),
  });
  vi.stubGlobal('document', { hidden: false });
  server.abbinaLettore.mockReset().mockResolvedValue(ABBINAMENTO);
  server.lettureLettore.mockReset().mockResolvedValue(letture());
  server.scollegaLettore.mockReset().mockResolvedValue(undefined);
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('il telefono come lettore', () => {
  it('abbinato, manda i codici alla pagina che ascolta per ultima', async () => {
    const l = await nuovoLettore();
    const tornello: string[] = [];
    const scheda: string[] = [];
    l.ascolta((c) => tornello.push(c));
    const smetti = l.ascolta((c) => scheda.push(c));

    server.lettureLettore.mockResolvedValueOnce(letture([1, 'AA11'], [2, 'BB22']));
    await l.abbina();
    await avanza(0);
    expect(server.lettureLettore).toHaveBeenLastCalledWith('abb-1', 0);
    expect(scheda).toEqual(['AA11', 'BB22']);
    expect(tornello).toEqual([]);
    expect(l.ultimo?.codice).toBe('BB22');
    expect(l.collegato).toBe(true);
    // il giro dopo chiede solo le letture nuove
    await avanza(700);
    expect(server.lettureLettore).toHaveBeenLastCalledWith('abb-1', 2);

    // chiusa la scheda del cliente, i codici tornano al tornello
    smetti();
    server.lettureLettore.mockResolvedValueOnce(letture([3, 'CC33']));
    await avanza(700);
    expect(tornello).toEqual(['CC33']);
    // l'abbinamento resta nella memoria della scheda, con l'ultima lettura vista
    expect(JSON.parse(memoria.get('lettore-telefono')!)).toMatchObject({ id: 'abb-1', dopo: 3 });
  });

  it('il telefono che non si fa vivo da un minuto non è più collegato', async () => {
    const l = await nuovoLettore();
    await l.abbina();
    await avanza(0);
    expect(l.collegato).toBe(true);
    server.lettureLettore.mockResolvedValue({ telefonoVistoIl: new Date(Date.now()).toISOString(), letture: [] });
    vi.setSystemTime(Date.now() + 61_000);
    expect(l.collegato).toBe(false);
  });

  it('con la scheda nascosta interroga il server più di rado', async () => {
    const l = await nuovoLettore();
    vi.stubGlobal('document', { hidden: true });
    await l.abbina();
    await avanza(0);
    const prima = server.lettureLettore.mock.calls.length;
    await avanza(2000);
    expect(server.lettureLettore.mock.calls.length).toBe(prima);
    await avanza(1000);
    expect(server.lettureLettore.mock.calls.length).toBe(prima + 1);
  });

  it('abbinamento scaduto (404): si ferma e chiede di rifarlo', async () => {
    const l = await nuovoLettore();
    await l.abbina();
    await avanza(0);
    server.lettureLettore.mockRejectedValueOnce(new ErroreApi(404, 'Non trovato'));
    await avanza(700);
    expect(l.abbinamento).toBeNull();
    expect(l.errore).toContain('scaduto');
    expect(memoria.has('lettore-telefono')).toBe(false);
    const chiamate = server.lettureLettore.mock.calls.length;
    await avanza(5000);
    expect(server.lettureLettore.mock.calls.length).toBe(chiamate);
  });

  it('server irraggiungibile: lo dice e riprova', async () => {
    const l = await nuovoLettore();
    await l.abbina();
    await avanza(0);
    server.lettureLettore.mockRejectedValueOnce(new ErroreApi(0, 'Il server non risponde.'));
    await avanza(700);
    expect(l.errore).toContain('riprovo');
    expect(l.abbinamento).not.toBeNull();
    await avanza(700);
    expect(l.errore).toBe('');
  });

  it('se l\'abbinamento non si può preparare lo dice', async () => {
    const l = await nuovoLettore();
    server.abbinaLettore.mockRejectedValueOnce(new ErroreApi(403, 'Non hai i permessi per questa operazione.'));
    await l.abbina();
    expect(l.errore).toBe('Non hai i permessi per questa operazione.');
    server.abbinaLettore.mockRejectedValueOnce(new Error('rotto'));
    await l.abbina();
    expect(l.errore).toContain('abbinamento');
  });

  it('ricaricata la pagina riprende l\'abbinamento da dove era', async () => {
    memoria.set('lettore-telefono', JSON.stringify({ ...ABBINAMENTO, dopo: 7 }));
    const l = await nuovoLettore();
    await avanza(0);
    expect(l.abbinamento?.id).toBe('abb-1');
    expect(server.lettureLettore).toHaveBeenCalledWith('abb-1', 7);
  });

  it('scollegato, avvisa il server e smette di chiedere', async () => {
    const l = await nuovoLettore();
    await l.abbina();
    await avanza(0);
    await l.scollega();
    expect(server.scollegaLettore).toHaveBeenCalledWith('abb-1');
    expect(l.abbinamento).toBeNull();
    expect(memoria.has('lettore-telefono')).toBe(false);
    const chiamate = server.lettureLettore.mock.calls.length;
    await avanza(5000);
    expect(server.lettureLettore.mock.calls.length).toBe(chiamate);
  });
});

describe('indirizzo da aprire sul telefono', () => {
  it('su un server vero resta quello', () => {
    expect(indirizzoPerTelefono('https://gestionale.leone.local', ['192.168.1.9'])).toEqual({ url: 'https://gestionale.leone.local', localhost: false, sviluppo: false });
  });
  it('con localhost usa l\'indirizzo del PC in rete, con la stessa porta', () => {
    expect(indirizzoPerTelefono('http://localhost:5173', ['192.168.1.9'])).toEqual({ url: 'http://192.168.1.9:5173', localhost: false, sviluppo: true });
  });
  it('con localhost e senza rete lo segnala', () => {
    expect(indirizzoPerTelefono('http://localhost:5173', [])).toEqual({ url: 'http://localhost:5173', localhost: true, sviluppo: true });
  });
});
