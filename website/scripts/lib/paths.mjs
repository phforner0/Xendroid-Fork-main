// Links relativos entre páginas: o site funciona em qualquer subdiretório (ex.: /Xendroid-Plus/)
// sem reescrever URLs. Uma página "docs/instalacao/" vira docs/instalacao/index.html.

/** Caminho relativo da página `from` (pasta, "" na raiz) até `to` (pasta ou arquivo, com #âncora opcional). */
export function rel(from, to) {
  const [target, hash] = to.split('#');
  const fromParts = from.split('/').filter(Boolean);
  const toParts = target.split('/').filter(Boolean);
  let i = 0;
  while (i < fromParts.length && i < toParts.length && fromParts[i] === toParts[i]) i++;
  const up = fromParts.length - i;
  let out = '../'.repeat(up) + toParts.slice(i).join('/');
  if (target.endsWith('/') && toParts.length > i) out += '/';
  if (out === '') out = './';
  return hash !== undefined ? `${out}#${hash}` : out;
}

/** Arquivo de saída de uma página. */
export const outFile = page => (page === '' ? 'index.html' : `${page.replace(/\/?$/, '/')}index.html`);

/** Âncora a partir de um título em português: "Instalação e atualização" → "instalacao-e-atualizacao". */
export function slugify(text) {
  return String(text)
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/<[^>]+>/g, '')
    .replace(/&[a-z]+;/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '') || 'secao';
}

export function uniqueSlugger() {
  const seen = new Map();
  return text => {
    const base = slugify(text);
    const n = seen.get(base) || 0;
    seen.set(base, n + 1);
    return n ? `${base}-${n + 1}` : base;
  };
}
