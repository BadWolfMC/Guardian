# Locale and message customization

Guardian keeps player- and administrator-visible text in locale property files. The packaged fallback is `en_us`.

## Files

Paper loads `plugins/Guardian/locales/<locale>.properties`. Velocity uses its Guardian data directory with the same `locales/<locale>.properties` layout.

The selected locale is configured in host `config.yml`. `en_us` remains the required complete fallback catalog.

## Safe customization rules

- Keep `schema-version=1` in locale files; locale schema is independent from configuration/policy schema 2.
- Preserve every required key. `validate` reports missing/invalid locale content before activation.
- Templates use MiniMessage syntax.
- Guardian inserts internal/player/configured values as unparsed placeholders where appropriate; do not convert untrusted values into executable MiniMessage markup.
- Keep disconnect messages concise enough to be useful on the Minecraft disconnect screen.
- On Paper, `messages.help-url` in `config.yml` is administrator-owned text inserted where `<help_url>` is present. Point it at the canonical Cerberus download/setup/support page for your deployment.
- On Velocity, the equivalent fallback destination is `meta.help-url` in the locale catalog; customize it before deployment if the packaged BadWolfMC URL is not appropriate.

## Workflow

1. copy `en_us.properties` to a new locale ID;
2. translate/customize values but retain keys;
3. set the host locale in `config.yml`;
4. run `/guardian validate` or `/guardianv validate`;
5. use the corresponding reload command only after validation succeeds.

A failed reload retains the prior active locale/runtime snapshot.

## Cerberus mismatch wording

The stock English timeout message intentionally tells players that if Cerberus itself reports an **untrusted Guardian server identity**, they should not bypass that warning and should contact staff. Guardian still reports the server-side outcome as `CERBERUS_TIMEOUT`; this improves diagnosis without asking Cerberus to disclose a manifest to an unauthenticated server or changing stable protocol semantics.
