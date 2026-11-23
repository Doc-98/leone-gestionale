import { describe, expect, it } from 'vitest';
import { misuraSegmentato } from './impila';

describe('segmentati impilati', () => {
  it('tutte le voci larghe quanto la più lunga', () => {
    const m = misuraSegmentato([69, 99, 69], 4, 8, 400);
    expect(m.colonna).toBe(99);
    expect(m.naturale).toBe(3 * 99 + 2 * 4 + 8);
    expect(m.impila).toBe(false);
  });
  it('si impila solo se in fila è più largo dello spazio', () => {
    expect(misuraSegmentato([122, 100], 4, 8, 256).impila).toBe(false); // 2×122+4+8 = 256: ci sta giusto
    expect(misuraSegmentato([122, 100], 4, 8, 254).impila).toBe(true);
  });
  it('una voce lunga su una riga stretta impila', () => {
    expect(misuraSegmentato([128, 114], 4, 8, 250).impila).toBe(true);
    expect(misuraSegmentato([69, 99, 69], 4, 8, 254).impila).toBe(true); // 3×99+8+8 = 313 > 254
  });
  it('senza una misura (riga non ancora disegnata) non si impila', () => {
    expect(misuraSegmentato([128, 114], 4, 8, 0).impila).toBe(false);
  });
});
