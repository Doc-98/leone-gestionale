import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Me } from './api';

/** Chi è collegato: il primo controllo, l'accesso, l'uscita, ruoli e permessi. Il server è finto. */
const server = vi.hoisted(() => ({ me: vi.fn(), login: vi.fn(), logout: vi.fn() }));

vi.mock('./api', async (originale) => {
  const vero = await originale<typeof import('./api')>();
  return { ...vero, api: server };
});

const { NonAutenticato, ErroreApi } = await import('./api');
const { sessione } = await import('./sessione.svelte');

const giulia = {
  accountId: 'acc-1',
  nome: 'Giulia',
  ruoli: [{ ruolo: 'SEGRETERIA' }],
  permessi: ['CLIENTI', 'PAGAMENTI'],
} as unknown as Me;

beforeEach(() => {
  server.me.mockReset();
  server.login.mockReset();
  server.logout.mockReset();
  sessione.utente = null;
  sessione.pronto = false;
});

afterEach(() => vi.restoreAllMocks());

describe('sessione', () => {
  it('al primo controllo con la sessione aperta ritrova chi è collegato', async () => {
    server.me.mockResolvedValue(giulia);
    await sessione.carica();
    expect(sessione.utente).toBe(giulia);
    expect(sessione.pronto).toBe(true);
  });

  it('senza sessione resta fuori, senza errori in console', async () => {
    const console_ = vi.spyOn(console, 'error').mockImplementation(() => {});
    server.me.mockRejectedValue(new NonAutenticato('Sessione scaduta.'));
    await sessione.carica();
    expect(sessione.utente).toBeNull();
    expect(sessione.pronto).toBe(true);
    expect(console_).not.toHaveBeenCalled();
  });

  it('un errore diverso finisce in console, ma la pagina parte lo stesso', async () => {
    const console_ = vi.spyOn(console, 'error').mockImplementation(() => {});
    server.me.mockRejectedValue(new ErroreApi(0, 'Il server non risponde.'));
    await sessione.carica();
    expect(sessione.pronto).toBe(true);
    expect(console_).toHaveBeenCalledOnce();
  });

  it('entra ed esce; anche se l\'uscita fallisce sul server, qui si esce', async () => {
    server.login.mockResolvedValue(giulia);
    await sessione.entra('giulia@esempio.it', 'una frase lunga');
    expect(server.login).toHaveBeenCalledWith('giulia@esempio.it', 'una frase lunga');
    expect(sessione.utente).toBe(giulia);
    server.logout.mockRejectedValue(new ErroreApi(0, 'Il server non risponde.'));
    await expect(sessione.esci()).rejects.toThrow();
    expect(sessione.utente).toBeNull();
  });

  it('credenziali sbagliate: l\'errore arriva alla pagina di accesso', async () => {
    server.login.mockRejectedValue(new ErroreApi(401, 'Email o password non corrette.'));
    await expect(sessione.entra('x@y.it', 'no')).rejects.toBeInstanceOf(ErroreApi);
    expect(sessione.utente).toBeNull();
  });

  it('ruoli e permessi', () => {
    expect(sessione.ha('SEGRETERIA')).toBe(false);
    expect(sessione.puo('CLIENTI')).toBe(false);
    sessione.utente = giulia;
    expect(sessione.ha('SEGRETERIA')).toBe(true);
    expect(sessione.ha('COORDINATORE', 'SEGRETERIA')).toBe(true);
    expect(sessione.ha('COORDINATORE')).toBe(false);
    expect(sessione.puo('RUOLI', 'PAGAMENTI')).toBe(true);
    expect(sessione.puo('RUOLI')).toBe(false);
    sessione.utente = { ...giulia, permessi: undefined } as unknown as Me;
    expect(sessione.puo('CLIENTI')).toBe(false);
  });

  it('aggiorna ruoli e permessi; se il server non risponde resta com\'è', async () => {
    sessione.utente = giulia;
    const promossa = { ...giulia, permessi: ['CLIENTI', 'PAGAMENTI', 'RUOLI'] } as unknown as Me;
    server.me.mockResolvedValueOnce(promossa);
    await sessione.aggiorna();
    expect(sessione.puo('RUOLI')).toBe(true);
    server.me.mockRejectedValueOnce(new ErroreApi(0, 'Il server non risponde.'));
    await sessione.aggiorna();
    expect(sessione.utente).toEqual(promossa);
  });
});
