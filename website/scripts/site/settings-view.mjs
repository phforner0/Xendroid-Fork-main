// Como mostrar um ajuste do app: rótulo de um valor, nível, quando vale. Mesmas regras da
// interface do app (XdSettings.kt): bool vira Ligado/Desligado, lista mostra o rótulo da opção.
import { slugify } from '../lib/paths.mjs';

export const LEVEL_LABEL = { ESSENTIAL: 'Essencial', ADVANCED: 'Avançado', ALL: 'Tudo' };

export function settingAnchor(s) {
  return `ajuste-${slugify(s.section || 'app')}-${slugify(s.name || s.key)}`;
}

export function textOf(t) {
  if (t == null) return '';
  return typeof t === 'string' ? t : t.text;
}

/** Rótulo de um valor guardado (texto cru) do ajuste. */
export function valueLabel(s, raw, texts) {
  const v = raw == null ? '' : String(raw);
  if (s.type === 'bool') return v === 'true' ? textOf(texts.on) : textOf(texts.off);
  if (s.type === 'list') {
    const o = (s.options || []).find(x => String(x.value) === v);
    if (o) return textOf(o.label);
    if (v === '' && s.options && s.options.length) return textOf(s.options[0].label);
    return v;
  }
  if (s.type === 'int') return v === '' ? '—' : `${Number(v).toLocaleString('pt-BR')}${s.unit || ''}`;
  if (s.type === 'text' || s.type === 'action') return v === '' ? 'vazio' : v;
  return v;
}

/** Texto curto de quando o valor vale. */
export function applyLabel(s) {
  if (s.type === 'action') return 'ação, roda na hora';
  if (s.live) return 'na próxima abertura; também muda ao vivo pelo menu em jogo';
  return 'na próxima abertura do jogo';
}
