// Simulador da interface (provisório: a página definitiva vem com o porte do protótipo).
import { html } from '../lib/html.mjs';

export const SIM_SCREENS = ['library', 'game', 'settings', 'drivers', 'loading', 'launchfail', 'ingame', 'hud', 'controls', 'keymap', 'touchedit', 'padtest', 'phonepad', 'profiles', 'saves', 'content', 'diagnostics', 'compare', 'firstrun', 'folders', 'browse', 'missing', 'novulkan', 'update', 'about', 'msgbox', 'keyboard', 'discswap'];

export function writeSimulator() {
  return { hash: 'provisorio' };
}

export function simulatorPage(site) {
  return {
    page: { path: 'simulador/', section: 'simulador', title: 'Simulador da interface', description: 'Simulador da interface do Xendroid+.' },
    content: html`<section class="section"><div class="wrap"><h1>Simulador</h1></div></section>`,
  };
}
