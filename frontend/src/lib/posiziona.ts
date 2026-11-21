/**
 * Dove mettere una bolla o un pannello rispetto a ciò che li apre, senza uscire dalla schermata.
 * Funzioni pure sulle misure (coordinate di finestra, come `getBoundingClientRect`): le usano il fumetto «i»
 * e, dopo, il menu «⋯».
 */
export interface Rett { x: number; y: number; w: number; h: number }
/** `punta` è la x della punta della bolla, misurata dal bordo sinistro della bolla. */
export interface Posizione { x: number; y: number; sotto: boolean; punta: number }

const limita = (v: number, min: number, max: number) => Math.max(min, Math.min(v, max));

/** Sotto se ci sta; altrimenti sopra se ci sta; se non ci sta né l'uno né l'altro, sotto (e la pagina scorre). */
function stoSotto(ancora: Rett, altezza: number, vistaH: number, margine: number, distanza: number): boolean {
  const spazioSotto = vistaH - (ancora.y + ancora.h) - distanza - margine;
  const spazioSopra = ancora.y - distanza - margine;
  return spazioSotto >= altezza || spazioSopra < altezza;
}

/**
 * La bolla sotto l'àncora (sopra se sotto non c'è posto), spostata di lato per restare a `margine` dai bordi;
 * la punta segue l'àncora ed è tenuta ad almeno `raggio` dagli angoli della bolla.
 */
export function posizionaBolla(
  ancora: Rett,
  bolla: { w: number; h: number },
  vista: { w: number; h: number },
  opz: { margine?: number; distanza?: number; raggio?: number } = {},
): Posizione {
  const { margine = 16, distanza = 8, raggio = 12 } = opz;
  const centro = ancora.x + ancora.w / 2;
  const x = limita(centro - bolla.w / 2, margine, Math.max(margine, vista.w - margine - bolla.w));
  const sotto = stoSotto(ancora, bolla.h, vista.h, margine, distanza);
  const y = sotto ? ancora.y + ancora.h + distanza : ancora.y - distanza - bolla.h;
  const punta = limita(centro - x, raggio, Math.max(raggio, bolla.w - raggio));
  return { x, y, sotto, punta };
}

/**
 * Il pannello del menu sotto il pulsante, allineato al bordo destro del pulsante (o al sinistro se non sta),
 * sopra se sotto non c'è posto; mai fuori dai margini.
 */
export function posizionaMenu(
  ancora: Rett,
  pannello: { w: number; h: number },
  vista: { w: number; h: number },
  opz: { margine?: number; distanza?: number } = {},
): { x: number; y: number; sotto: boolean } {
  const { margine = 16, distanza = 8 } = opz;
  const max = Math.max(margine, vista.w - margine - pannello.w);
  const adestra = ancora.x + ancora.w - pannello.w;
  const x = limita(adestra >= margine ? adestra : ancora.x, margine, max);
  const sotto = stoSotto(ancora, pannello.h, vista.h, margine, distanza);
  const y = sotto ? ancora.y + ancora.h + distanza : ancora.y - distanza - pannello.h;
  return { x, y, sotto };
}
