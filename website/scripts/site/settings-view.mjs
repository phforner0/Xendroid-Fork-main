// Como mostrar um ajuste do app: rótulo de um valor, nível, quando vale. Mesmas regras da
// interface do app (XdSettings.kt): bool vira Ligado/Desligado (On/Off), lista mostra o rótulo
// da opção. `lang` é o idioma da página ('en' ou 'pt'); os textos vêm do próprio app.
import { slugify } from '../lib/paths.mjs';
import { textIn } from './i18n.mjs';

const LEVEL_TEXT = { ESSENTIAL: 'levelEssential', ADVANCED: 'levelAdvanced', ALL: 'levelAll' };

/** Nome do nível como o app mostra (Essencial, Avançado, Tudo / Essential, Advanced, All). */
export function levelLabel(level, texts, lang = 'pt') {
  return textIn(texts[LEVEL_TEXT[level]], lang) || level;
}

export function settingAnchor(s, lang = 'pt') {
  return `${lang === 'en' ? 'setting' : 'ajuste'}-${slugify(s.section || 'app')}-${slugify(s.name || s.key)}`;
}

export function textOf(t, lang = 'pt') {
  return textIn(t, lang);
}

/** Rótulo de um valor guardado (texto cru) do ajuste. */
export function valueLabel(s, raw, texts, lang = 'pt') {
  const v = raw == null ? '' : String(raw);
  if (s.type === 'bool') return v === 'true' ? textOf(texts.on, lang) : textOf(texts.off, lang);
  if (s.type === 'list') {
    const o = (s.options || []).find(x => String(x.value) === v);
    if (o) return textOf(o.label, lang);
    if (v === '' && s.options && s.options.length) return textOf(s.options[0].label, lang);
    return v;
  }
  if (s.type === 'int') return v === '' ? '—' : `${Number(v).toLocaleString(lang === 'en' ? 'en-US' : 'pt-BR')}${s.unit || ''}`;
  if (s.type === 'text' || s.type === 'action') return v === '' ? (lang === 'en' ? 'empty' : 'vazio') : v;
  return v;
}

/** Texto curto de quando o valor vale. */
export function applyLabel(s, lang = 'pt') {
  const en = lang === 'en';
  if (s.type === 'action') return en ? 'an action, runs right away' : 'ação, roda na hora';
  if (s.live) return en ? 'at the next launch; can also change live from the in-game menu' : 'na próxima abertura; também muda ao vivo pelo menu em jogo';
  return en ? 'at the next launch of the game' : 'na próxima abertura do jogo';
}
