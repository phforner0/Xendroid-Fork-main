# Ajustes da comunidade (15b): protocolo v1, privacidade e servidor de referência

Estado: **Implementado e testado localmente** (JVM + servidor de referência + ponta a ponta na
nuvem). **Desligado por padrão**: só aparece num build gerado com
`-PxendroidCommunityUrl=https://<host>[/<prefixo>]`. Não há servidor público do projeto; quem
quiser oferecer o recurso roda o servidor de referência (abaixo) atrás de um proxy com TLS.
Validação no aparelho pendente (ver roteiro de aparelho na auditoria, item 53).

## O que o jogador vê

Nas configurações por jogo, abaixo dos ajustes recomendados (C05), o cartão **Ajustes da
comunidade**:

- **Buscar** envia ao servidor só o Title ID do jogo e mostra o que outros jogadores
  compartilharam, do aparelho mais parecido para o menos parecido: mesmo modelo, mesmo chip
  (`Build.SOC_MODEL`, Android 12+), mesma GPU, GPU da mesma geração (Adreno 7xx, Mali-G7xx,
  Xclipse 9xx), outro hardware; dentro de cada grupo, os mais bem votados e depois os mais novos.
  Abrir a tela não contata o servidor: mostra a última lista buscada, com a data.
- Cada ajuste mostra nome, o que resolve (nota de quem enviou), o resultado que essa pessoa viu
  (as mesmas categorias do C03), o aparelho, o build do XenDroid e a data.
- **Pré-visualizar** usa exatamente o caminho do C05: mesma lista de chaves permitidas e valores
  válidos, prévia de cada mudança, ajustes que o jogador definiu para o jogo ficam (os dele
  vencem), e **Restaurar anterior** desfaz o que foi escrito. O registro aplicado guarda a origem
  `COMMUNITY`.
- **Ajudou / Não ajudou**: um voto por aparelho e por ajuste; tocar de novo no mesmo voto o
  retira. Um ajuste com 5+ votos negativos e mais que o dobro dos positivos fica oculto (contado).
- **Compartilhar os ajustes deste jogo** abre um diálogo com nome (até 60), o que os ajustes
  resolvem (até 500), o resultado visto e a lista exata do que vai ser enviado. Nada sai antes de
  tocar **Compartilhar**.
- Os ajustes enviados por este aparelho podem ser apagados (o token de exclusão fica só aqui).

## O que é enviado (privacidade)

| Ação | Enviado |
|---|---|
| Buscar | Title ID (no caminho da URL) |
| Votar | id do ajuste, voto (-1, 0, 1) e um `voter` = SHA-256(segredo local ‖ ":" ‖ id do ajuste), 32 hex |
| Compartilhar | Title ID, nome, nota (passada pelo `LogRedactor`: caminhos, e-mails, IPs, credenciais), resultado, os ajustes do jogo que estão na lista permitida do C05 com valores válidos (no máximo 32), fabricante, modelo, chip, GPU, rótulo do driver (redigido, até 64), nível de API do Android, `versionCode` e `versionName` do app |
| Apagar | id do ajuste e o token de exclusão (cabeçalho `X-Delete-Token`) |

Nunca: conta, gamertag/XUID, nomes ou caminhos de arquivo, caminho do driver
(`vulkan_lib_path`), log, rede, idioma/região, patches, nem um identificador da instalação. O
`voter` muda de ajuste para ajuste, então os votos de um aparelho não podem ser ligados entre si.
O servidor vê o endereço IP de quem conecta, como qualquer site; o servidor de referência não o
grava em log e só o usa, em memória, para limitar a frequência.

O cliente só fala HTTPS (um endereço `http://` só é aceito no próprio computador, em testes),
não segue redirecionamentos e limita o tamanho das respostas (lista 512 KB; demais 4 KB).

## Protocolo v1

Base: a URL do build (`https://host[/prefixo]`). Corpo JSON UTF-8. Erros respondem
`{"error": "<motivo legível>"}`.

### `GET /v1/titles/{TITLEID}/configs`

```json
{
  "format": "xendroid-community-configs",
  "version": 1,
  "titleId": "4D5307E6",
  "configs": [
    {
      "id": "0123456789abcdef",
      "titleId": "4D5307E6",
      "name": "Steady 30",
      "note": "Holds 30 FPS where 60 stutters",
      "result": "PLAYABLE",
      "settings": {"GPU|framerate_limit": "30", "Console|widescreen": "true"},
      "device": {"manufacturer": "samsung", "model": "SM-S911B", "soc": "SM8550",
                 "gpu": "Adreno (TM) 740", "driver": "Qualcomm 0.762", "androidSdk": 34},
      "appVersionCode": 120,
      "appBuild": "1.2.0",
      "createdAt": "2026-10-03",
      "votesUp": 3,
      "votesDown": 0
    }
  ]
}
```

No máximo 100 ajustes, em ordem de votos líquidos. O app revalida tudo: `format`/`version`
(outro valor descarta a lista inteira), Title ID, id `[a-z0-9]{8,32}`, resultado conhecido,
nome 1–60, nota 1–500, campos do aparelho até 64, data `AAAA-MM-DD`, votos 0–1 000 000, e cada
ajuste pelas regras do C05 (`SettingsProfiles.problems`); o que falha é ignorado com o motivo.
Campos desconhecidos são ignorados (uma mudança de significado sobe a versão).

### `POST /v1/configs`

Corpo com exatamente os campos `titleId`, `name`, `note`, `result`, `settings`, `device`,
`appVersionCode`, `appBuild`. Resposta `201 {"id": "...", "deleteToken": "..."}`. O servidor
guarda só o SHA-256 do token. `409` quando o jogo já tem o máximo de ajustes guardados (300).

### `POST /v1/configs/{id}/vote`

Corpo `{"voter": "<32 hex>", "vote": -1 | 0 | 1}` (0 retira). Resposta
`200 {"votesUp": n, "votesDown": m}`; `404` para id desconhecido.

### `DELETE /v1/configs/{id}`

Cabeçalho `X-Delete-Token` (ou `X-Admin-Token`, para moderação). `204`; `403` token errado;
`404` já não existe (o app então esquece o ajuste localmente).

### Limites do servidor de referência

Por endereço e hora: 10 envios, 120 votos, 30 exclusões (`429` além disso). Corpo até 16 KB
(`413`), só `application/json` (`415`), chaves JSON repetidas recusadas.

## Servidor de referência

`tools/community-server/server.py` — Python 3, só biblioteca padrão (`http.server` + `sqlite3`).

```sh
python3 tools/community-server/server.py --db /var/lib/xendroid/community.sqlite3 --port 8780
# atrás de um proxy TLS (Caddy/nginx) que repasse X-Forwarded-For:
python3 tools/community-server/server.py --db ... --trust-forwarded
# moderação: XENDROID_COMMUNITY_ADMIN_TOKEN=<16+ caracteres> habilita X-Admin-Token
```

- `contract.json` traz o formato, a versão, os resultados, os limites e a lista de chaves
  permitidas. `CommunityServerContractTest` (JVM) falha se ela divergir de
  `SettingsProfiles.ALLOWED_KEYS`, de `CompatStatus` ou dos limites do app.
- Testes do servidor: `python3 tools/community-server/test_server.py` (13 testes: protocolo,
  regras de cada campo, votos, exclusão por token e por moderador, limite por jogo, limite por
  endereço, contrato).
- Ponta a ponta: `CommunityServerContractTest.endToEndAgainstTheReferenceServer` sobe o
  servidor real com `python3` (pulado se não houver `python3`) e usa o cliente do app para
  compartilhar, listar com ranking, votar, retirar o voto e apagar.

Gerar um build que use um servidor:

```sh
./gradlew :app:assembleDebug -PxendroidCommunityUrl=https://community.example.org
```

## Arquivos

- App: `app/src/main/java/xendroid/compose/community/` (`CommunityConfigs` regras puras,
  `CommunityClient` HTTP, `CommunityStore` segredo/tokens/votos/última lista em arquivos
  privados, `CommunityService`), `settings/GameSettingsViewModel.kt` (seção 15b),
  `ui/settings/CommunityConfigsSection.kt` (cartão e diálogo de envio).
- Testes JVM: `app/src/test/java/xendroid/compose/community/`.
- Servidor: `tools/community-server/` (`server.py`, `contract.json`, `test_server.py`).
