// Resumo de uma release a partir das notas publicadas no GitHub. As notas novas trazem blocos
// ocultos <!-- update-summary:pt-BR … --> (tools/release_notes.py), os mesmos que o cartão de
// atualização do app mostra; nas antigas, usa os destaques em inglês antes de "Full Changelog".
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

export function releaseSummary(body) {
  const b = body || '';
  const pt = /<!--\s*update-summary:pt-BR\s*([\s\S]*?)-->/.exec(b);
  if (pt && bullets(pt[1]).length) return { lang: 'pt-BR', items: bullets(pt[1]).map(inlineMd) };
  const details = /<summary>[^<]*(?:Em português|Português)[^<]*<\/summary>([\s\S]*?)<\/details>/i.exec(b);
  if (details && bullets(details[1]).length) return { lang: 'pt-BR', items: bullets(details[1]).slice(0, 12).map(inlineMd) };
  const en = /<!--\s*update-summary:en\s*([\s\S]*?)-->/.exec(b);
  if (en && bullets(en[1]).length) return { lang: 'en', items: bullets(en[1]).map(inlineMd) };
  const head = b.split(/\*\*Full Changelog\*\*/)[0].replace(/<!--[\s\S]*?-->/g, '');
  return { lang: 'en', items: bullets(head).slice(0, 8).map(inlineMd) };
}
