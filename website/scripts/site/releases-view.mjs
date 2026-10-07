// Resumo de uma release a partir das notas publicadas no GitHub. As notas novas trazem blocos
// ocultos <!-- update-summary:pt-BR … --> e <!-- update-summary:en … --> (tools/release_notes.py),
// os mesmos que o cartão de atualização do app mostra; nas antigas, usa os destaques em inglês
// antes de "Full Changelog". As páginas em inglês usam o bloco en; as em português, o pt-BR.
import { esc, raw } from '../lib/html.mjs';

function bullets(text) {
  return text.split('\n').map(l => l.trim()).filter(l => /^[-*]\s+/.test(l)).map(l => l.replace(/^[-*]\s+/, ''));
}

/** Markdown de uma linha (negrito, código, links) em HTML seguro. */
export function inlineMd(s) {
  let out = esc(s);
  out = out.replace(/`([^`]+)`/g, '<code>$1</code>');
  out = out.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  out = out.replace(/\[([^\]]+)\]\((https:\/\/[^)\s]+)\)/g, (m, t, u) => `<a href="${u}" rel="noopener">${t}</a>`);
  return raw(out);
}

export function releaseSummary(body, lang = 'pt') {
  const b = body || '';
  const block = code => {
    const m = new RegExp(`<!--\\s*update-summary:${code}\\s*([\\s\\S]*?)-->`).exec(b);
    return m && bullets(m[1]).length ? bullets(m[1]).map(inlineMd) : null;
  };
  if (lang === 'pt') {
    const pt = block('pt-BR');
    if (pt) return { lang: 'pt-BR', items: pt };
    const details = /<summary>[^<]*(?:Em português|Português)[^<]*<\/summary>([\s\S]*?)<\/details>/i.exec(b);
    if (details && bullets(details[1]).length) return { lang: 'pt-BR', items: bullets(details[1]).slice(0, 12).map(inlineMd) };
  }
  const en = block('en');
  if (en) return { lang: 'en', items: en };
  const head = b.split(/\*\*Full Changelog\*\*/)[0].replace(/<!--[\s\S]*?-->/g, '').replace(/<details>[\s\S]*?<\/details>/g, '');
  return { lang: 'en', items: bullets(head).slice(0, 8).map(inlineMd) };
}
