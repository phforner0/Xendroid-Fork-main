// Idiomas do site: o inglês na raiz e o português em pt-br/. Cada página existe nos dois, com
// os endereços pareados (hreflang e o seletor de idioma). Aqui ficam o que muda por idioma:
// prefixo, endereços das páginas fixas, formatos de data, tamanho e número.

export const LANGS = {
  en: { id: 'en', html: 'en', og: 'en_US', locale: 'en-US', prefix: '', name: 'English', short: 'EN', sim: 'simulator/' },
  pt: { id: 'pt', html: 'pt-BR', og: 'pt_BR', locale: 'pt-BR', prefix: 'pt-br/', name: 'Português (Brasil)', short: 'PT', sim: 'simulador/' },
};
export const LANG_IDS = Object.keys(LANGS);
export const DEFAULT_LANG = 'en';
const TZ = 'America/Sao_Paulo';

/** Ferramentas de um idioma: tx('inglês', 'português'), endereços e formatos. */
export function lang(id) {
  const L = LANGS[id];
  if (!L) throw new Error(`idioma desconhecido: ${id}`);
  const pt = id === 'pt';
  return {
    ...L,
    pt,
    tx: (en, ptText) => (pt ? ptText : en),
    home: L.prefix,
    docs: `${L.prefix}docs/`,
    simPath: `${L.prefix}${L.sim}`,
    /** Data curta: 05/10/2026 em português, Oct 5, 2026 em inglês (sem a ambiguidade do 10/5). */
    date(iso) {
      if (!iso) return '';
      const d = new Date(iso);
      return pt
        ? d.toLocaleDateString('pt-BR', { timeZone: TZ, day: '2-digit', month: '2-digit', year: 'numeric' })
        : d.toLocaleDateString('en-US', { timeZone: TZ, month: 'short', day: 'numeric', year: 'numeric' });
    },
    /** Data por extenso: 5 de outubro de 2026 / October 5, 2026. */
    dateLong(iso) {
      return iso ? new Intl.DateTimeFormat(L.locale, { dateStyle: 'long', timeZone: TZ }).format(new Date(iso)) : '';
    },
    num: n => Number(n).toLocaleString(L.locale),
    /** Bytes em MB ou KB (1 MB = 1024 KB, como as notas das releases). */
    bytes(n) {
      if (n == null) return '';
      if (n >= 1024 * 1024) return `${(n / 1024 / 1024).toLocaleString(L.locale, { maximumFractionDigits: 1 })} MB`;
      return `${Math.round(n / 1024).toLocaleString(L.locale)} KB`;
    },
    /** Lista com "e"/"and": a, b e c. */
    list(items) {
      return new Intl.ListFormat(L.locale, { style: 'long', type: 'conjunction' }).format(items);
    },
  };
}

/** Texto de um dado extraído do app no idioma da página ({ text, lang, en } ou string). */
export function textIn(t, id) {
  if (t == null) return '';
  if (typeof t === 'string') return t;
  return id === 'en' ? (t.en ?? t.text) : t.text;
}

/** Idioma em que o texto aparece numa página do idioma `id` (para marcar lang="en" no português). */
export function langOfText(t, id) {
  if (t == null || typeof t === 'string') return null;
  if (id === 'en') return 'en';
  return t.lang || 'pt-BR';
}
