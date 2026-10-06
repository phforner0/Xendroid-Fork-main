// Blocos gerados para a documentação: {{> nome arg=valor}} numa linha do Markdown.
// Cada bloco lê os dados extraídos do código; nenhum traz número ou lista escrito à mão.
import { html, raw, esc, icon, fmtBytes, fmtDate, langText } from '../lib/html.mjs';
import { slugify } from '../lib/paths.mjs';
import { LEVEL_LABEL, settingAnchor, textOf, valueLabel, applyLabel } from './settings-view.mjs';
import { releaseSummary } from './releases-view.mjs';

const t = x => raw(langText(x));

function tableWrap(label, head, rows) {
  return html`<div class="table-wrap" tabindex="0" role="region" aria-label="${label}"><table><thead><tr>${head.map(h => html`<th scope="col">${h}</th>`)}</tr></thead><tbody>${rows}</tbody></table></div>`;
}

function settingBlock(site, s, ch) {
  const texts = ch.settings.texts;
  const def = s.type === 'action' ? null : valueLabel(s, s.default, texts);
  const opts = s.type === 'list' ? s.options.map(o => textOf(o.label)) : null;
  const range = s.type === 'int' && s.min != null ? `de ${s.min.toLocaleString('pt-BR')} a ${s.max.toLocaleString('pt-BR')}${s.unit || ''}${s.step && s.step !== 1 ? `, de ${s.step} em ${s.step}` : ''}` : null;
  const dep = s.dependsOn ? ch.settings.settings.find(x => x.key === s.dependsOn.key) : null;
  return html`<div class="setting" id="${settingAnchor(s)}">
  <h4>${t(s.title)} <span class="key">${s.section ? `[${s.section}] ${s.name}` : s.key}</span>${s.isNew ? html` <span class="tag info">novo na interface</span>` : ''}</h4>
  <p>${t(s.desc)}</p>
  <p class="facts-line">
    ${def != null ? html`<span>Padrão: <b>${def}</b></span>` : ''}
    <span>Nível: <b>${LEVEL_LABEL[s.level]}</b></span>
    <span>Vale ${applyLabel(s)}</span>
    ${range ? html`<span>Faixa: ${range}</span>` : ''}
  </p>
  ${opts ? html`<p class="opts">Opções: ${opts.join(' · ')}</p>` : ''}
  ${dep ? html`<p class="opts">${icon('info', 15)} Só vale com “${textOf(dep.title)}” em ${valueLabel(dep, s.dependsOn.value, texts)}.</p>` : ''}
  ${s.warning ? html`<p class="opts">${icon('warn', 15)} ${t(s.warning)}</p>` : ''}
</div>`;
}

export function createBlocks(site) {
  const ch = () => site.ch.est;
  const groupTitle = id => textOf(ch().settings.groups.find(g => g.id === id)?.title) || id;

  return {
    /** Os ajustes de um grupo (ou todos), na ordem da interface. */
    'ajustes'(args) {
      const c = ch();
      const groups = args.grupo ? [args.grupo] : c.settings.groups.map(g => g.id);
      const headings = [];
      const out = groups.map(gid => {
        const list = c.settings.settings.filter(s => s.group === gid).sort((a, b) => a.order - b.order);
        if (!list.length) throw new Error(`grupo de ajustes desconhecido ou vazio: ${gid}`);
        if (args.grupo) return list.map(s => settingBlock(site, s, c)).join('');
        const id = `grupo-${slugify(groupTitle(gid))}`;
        headings.push({ level: 2, id, text: groupTitle(gid) });
        return html`<h2 id="${id}">${groupTitle(gid)} <a class="anchor" href="#${id}" aria-label="Link para esta seção: ${groupTitle(gid)}">#</a></h2>
<p class="u-muted">${list.length} ajustes · Essencial ${list.filter(s => s.level === 'ESSENTIAL').length}, Avançado ${list.filter(s => s.level === 'ADVANCED').length}, Tudo ${list.filter(s => s.level === 'ALL').length}</p>
${raw(list.map(s => settingBlock(site, s, c)).join(''))}`;
      }).join('');
      return { html: out, headings };
    },

    /** Contagem por grupo e nível, numa tabela. */
    'ajustes-resumo'() {
      const c = ch();
      const rows = c.settings.groups.map(g => {
        const list = c.settings.settings.filter(s => s.group === g.id);
        const n = l => list.filter(s => s.level === l).length;
        return html`<tr><th scope="row"><a href="../referencia-de-ajustes/#grupo-${slugify(textOf(g.title))}">${t(g.title)}</a></th><td>${n('ESSENTIAL')}</td><td>${n('ADVANCED')}</td><td>${n('ALL')}</td><td>${list.length}</td></tr>`;
      });
      return { html: String(tableWrap('Ajustes por grupo e nível', ['Grupo', 'Essencial', 'Avançado', 'Tudo', 'Total'], rows)) };
    },

    /** Diferenças de ajustes entre a estável e o desenvolvimento. */
    'ajustes-diferencas'() {
      if (site.data.sameChannels || !site.ch.dev) return { html: String(html`<p>A ${site.v.stable ? site.v.stable.label.toLowerCase() : 'versão estável'} e o main atual têm os mesmos ajustes.</p>`) };
      const a = new Map(site.ch.est.settings.settings.map(s => [s.key, s]));
      const b = new Map(site.ch.dev.settings.settings.map(s => [s.key, s]));
      const added = [...b.keys()].filter(k => !a.has(k));
      const removed = [...a.keys()].filter(k => !b.has(k));
      const changed = [...b.keys()].filter(k => a.has(k) && (String(a.get(k).default) !== String(b.get(k).default) || JSON.stringify(a.get(k).options) !== JSON.stringify(b.get(k).options)));
      if (!added.length && !removed.length && !changed.length) return { html: '<p>Os ajustes são os mesmos nas duas versões.</p>' };
      const li = (k, s) => html`<li><code>${k}</code> ${s ? html`(${t(s.title)})` : ''}</li>`;
      return { html: String(html`${added.length ? html`<p>Novos no desenvolvimento:</p><ul>${added.map(k => li(k, b.get(k)))}</ul>` : ''}${removed.length ? html`<p>Saíram no desenvolvimento:</p><ul>${removed.map(k => li(k, a.get(k)))}</ul>` : ''}${changed.length ? html`<p>Com padrão ou opções diferentes:</p><ul>${changed.map(k => li(k, b.get(k)))}</ul>` : ''}`) };
    },

    'predefinicoes'() {
      const c = ch();
      const byKey = new Map(c.settings.settings.map(s => [s.key, s]));
      const rows = c.settings.presets.map(p => html`<tr><th scope="row">${t(p.title)}</th><td>${t(p.description)}</td><td>${Object.entries(p.values).map(([k, v], i) => {
        const s = byKey.get(k);
        return html`${i ? '; ' : ''}${s ? textOf(s.title) : k}: ${s ? valueLabel(s, v, c.settings.texts) : v}`;
      })}</td></tr>`);
      return { html: String(tableWrap('Predefinições', ['Predefinição', 'Para quê', 'O que muda'], rows)) };
    },

    'turnip-flags'() {
      const rows = ch().settings.turnipFlags.map(f => html`<tr><th scope="row"><code>${f.name}</code></th><td>${t(f.help)}${f.exclusive ? html` <span class="tag plain">exclui ${f.name === 'sysmem' ? 'gmem' : 'sysmem'}</span>` : ''}</td></tr>`);
      return { html: String(tableWrap('Flags do Turnip', ['Flag', 'O que faz'], rows)) };
    },

    'correcoes'() {
      const g = ch().games;
      const rows = g.quirkTitles.map(q => html`<tr><td><code>${q.titleId}</code></td><th scope="row">${q.name || 'sem nome conhecido'}</th><td>${q.entries.length}</td><td>${q.entries.slice(0, 4).map((e, i) => html`${i ? ', ' : ''}<code>${e.cvar}</code>`)}${q.entries.length > 4 ? ` e mais ${q.entries.length - 4}` : ''}</td></tr>`);
      return { html: String(tableWrap('Correções automáticas por jogo', ['Title ID', 'Jogo', 'Ajustes', 'Cvars'], rows)) };
    },

    'desempenho-readme'() {
      const perf = site.ch.dev.games.perf;
      if (!perf) throw new Error('README.pt-BR.md sem a tabela "## Desempenho"');
      const rows = perf.rows.map(row => html`<tr>${perf.header.map((h, i) => (i === 0 ? html`<th scope="row">${row[h]}</th>` : html`<td>${row[h] || '—'}</td>`))}</tr>`);
      return { html: String(html`<p>${perf.context.replace(/\*\*/g, '')}</p>${tableWrap('Medições do README', perf.header, rows)}`) };
    },

    'requisitos'() {
      const a = ch().app;
      return { html: String(html`<dl class="spec">
<dt>Android</dt><dd>${a.minAndroid} ou mais novo (API ${a.minSdk}); feito para o Android ${a.targetAndroid} (API ${a.targetSdk})</dd>
<dt>ABI</dt><dd><span class="u-mono">${a.abi}</span>, só ARM de 64 bits</dd>
<dt>GPU</dt><dd>com Vulkan; sem Vulkan o app não roda jogos</dd>
<dt>Pacote</dt><dd><span class="u-mono">${a.package}</span> (o APK publicado)</dd>
</dl>`) };
    },

    'pastas'() {
      const a = ch().app;
      const rows = [
        ['Pasta de dados do app', a.dataRoot],
        ['Config global do emulador', a.globalConfig],
        ['Config de cada jogo', a.gameConfigPattern],
      ].map(([k, v]) => html`<tr><th scope="row">${k}</th><td><code>${v}</code></td></tr>`);
      return { html: String(tableWrap('Pastas e arquivos do app', ['O quê', 'Caminho no armazenamento interno'], rows)) };
    },

    'releases'(args) {
      const list = site.data.releases.releases.filter(r => r.build != null || args.todas === 'sim');
      const out = list.slice(0, Number(args.max || 20)).map(r => {
        const sum = releaseSummary(r.body);
        const id = `build-${r.build ?? r.tag.toLowerCase()}`;
        return html`<section class="release" aria-labelledby="${id}">
<h3 id="${id}">${r.build != null ? `Build ${r.build}` : r.name} <a class="anchor" href="#${id}" aria-label="Link para esta versão">#</a></h3>
<p class="page-meta"><span>${fmtDate(r.publishedAt)}</span><span class="u-mono">${r.tagSha ? r.tagSha.slice(0, 8) : ''}</span>${r.apk ? html`<a href="${r.apk.url}">APK, ${fmtBytes(r.apk.size)}</a>` : ''}<a href="${r.url}" rel="noopener">Notas completas no GitHub</a></p>
${sum.items.length ? html`<ul>${sum.items.map(i => html`<li>${i}</li>`)}</ul>` : html`<p class="u-muted">Sem resumo nas notas desta versão.</p>`}
${sum.lang === 'en' ? html`<p class="u-muted" style="font-size:var(--step--1)">Resumo só em inglês nas notas desta versão.</p>` : ''}
</section>`;
      });
      return { html: out.join(''), headings: list.slice(0, Number(args.max || 20)).map(r => ({ level: 3, id: `build-${r.build ?? r.tag.toLowerCase()}`, text: r.build != null ? `Build ${r.build}` : r.name })) };
    },

    'nao-lancado'() {
      const u = site.data.unreleased;
      if (u == null) return { html: '<p>Não deu para ler a história do main neste build.</p>' };
      if (!u.length) return { html: String(html`<p>Nada: o main não tem mudanças no app depois da ${site.v.stable.label.toLowerCase()}.</p>`) };
      return { html: String(html`<ul>${u.map(c => html`<li><a href="${site.config.repoUrl}/commit/${c.sha}" rel="noopener"><code>${c.sha.slice(0, 8)}</code></a> ${c.subject} <span class="u-muted">(${fmtDate(c.date)})</span></li>`)}</ul>`) };
    },

    'consistencia'() {
      const est = site.ch.est;
      const w = est.consistency.warnings;
      const cd = est.consistency.coreDefaults;
      const probs = site.data.problems.filter(p => p.level !== 'error');
      const src = s => (s && /^[\w./-]+:\d+$/.test(s) ? html`<a href="${site.config.repoUrl}/blob/${site.v.stable ? site.v.stable.commit : 'main'}/${s.replace(/:(\d+)$/, '#L$1')}" rel="noopener"><code>${s}</code></a>` : s ? html`<code>${s}</code>` : '');
      return { html: String(html`
<h3 id="avisos-do-codigo">Avisos sobre o código (${w.length + probs.length})</h3>
${w.length + probs.length ? html`<ul>${w.map(x => html`<li>${x.msg}${x.source ? html` · ${src(x.source)}` : ''}</li>`)}${probs.map(p => html`<li>${p.msg}</li>`)}</ul>` : html`<p>Nenhum.</p>`}
<h3 id="padroes-app-core">Padrões do app diferentes dos do núcleo (${cd.length})</h3>
<p>O app grava o valor dele na primeira abertura (o do modelo <code>default_config.toml</code>, ou o do esquema quando o modelo não traz a chave); o núcleo só usaria o próprio padrão se a chave faltasse no arquivo.</p>
${tableWrap('Padrões do app e do núcleo', ['Chave', 'App', 'Núcleo', 'Definição no núcleo'], cd.map(x => html`<tr><td><code>${x.key}</code></td><td><code>${x.app === '' ? '""' : x.app}</code> <span class="u-muted">(${x.appFrom})</span></td><td><code>${x.core === '' ? '""' : x.core}</code></td><td>${src(x.cvarSource.replace(/^xenia\//, 'emulator-core/src/main/cpp/xenia/'))}</td></tr>`))}
`), headings: [{ level: 3, id: 'avisos-do-codigo', text: 'Avisos sobre o código' }, { level: 3, id: 'padroes-app-core', text: 'Padrões do app diferentes dos do núcleo' }] };
    },

    'fontes'() {
      const rows = site.sourcesTable.map(r => html`<tr><th scope="row">${r.what}</th><td>${r.files.map((f, i) => html`${i ? ', ' : ''}<a href="${site.config.repoUrl}/blob/main/${f}" rel="noopener"><code>${f}</code></a>`)}</td><td>${r.how}</td><td>${r.channel}</td></tr>`);
      return { html: String(tableWrap('Fontes dos dados do site', ['Dado', 'Arquivo de origem', 'Como é lido', 'Versão'], rows)) };
    },

    'print'(args, env) {
      if (!args.id) throw new Error('{{> print}} precisa de id=');
      return { html: String(site.shot(env.pagePath, args.id, { caption: args.legenda ? raw(esc(args.legenda)) : null, portrait: args.retrato === 'sim' })) };
    },
  };
}
