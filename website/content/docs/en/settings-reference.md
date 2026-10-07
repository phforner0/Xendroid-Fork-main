---
title: Settings reference
description: Every emulator setting the app shows, with its name in the interface, TOML key, default, options, level and when each one takes effect.
section: referencia
order: 1
pt: referencia-de-ajustes
---

This page is generated from the app's code in {{estavel.nome}}: the settings schema, the interface catalog, the English strings and the `default_config.toml` template. It changes by itself when the app changes.

- **Name**: as it appears in the app.
- **Key**: the section and name in the configuration file, as in `[GPU] framerate_limit`. The site search (<kbd>/</kbd>) finds a setting by its key.
- **Default**: the value the app writes on first launch. When the `default_config.toml` template has the key, its value is used; otherwise, the one from the app's schema.
- **Level**: Essential, Advanced or All (see [levels](doc:settings#levels)).

{{> ajustes-resumo}}

## Differences between versions

{{> ajustes-diferencas}}

{{> ajustes}}
