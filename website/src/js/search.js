// Busca da documentação no navegador (MiniSearch). O índice é gerado no build, um por idioma
// (assets/search-en.json, assets/search-pt.json), com as seções de cada página e cada ajuste do app.
import MiniSearch from './vendor/minisearch.js';

const fold = s => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

export async function createSearch(url) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`índice da busca: ${res.status}`);
  const { docs } = await res.json();
  const ms = new MiniSearch({
    fields: ['t', 'k', 'x', 'p'],
    storeFields: ['t', 'p', 'u', 'x'],
    processTerm: term => fold(term),
    searchOptions: { boost: { t: 3, k: 2.5, p: 0.6 }, prefix: true, fuzzy: 0.18, combineWith: 'AND' },
    // chaves como GPU|framerate_limit viram termos separados
    tokenize: text => text.split(/[\s\-|_.,;:!?()[\]{}"'“”«»/\\=<>+*#@]+/u).filter(Boolean),
  });
  ms.addAll(docs);
  return {
    search(q) {
      let hits = ms.search(q);
      if (!hits.length) hits = ms.search(q, { combineWith: 'OR' });
      return hits;
    },
  };
}
