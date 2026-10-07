// Liga o simulador: as regras do formato de configuração (as mesmas do build do site) ficam
// disponíveis para os scripts do protótipo, e só então ele começa.
import * as shape from './config-shape.js';

window.XDR_TOML = shape;
// boot() vem de core.js (scripts clássicos, já carregados quando este módulo roda)
window.boot();
