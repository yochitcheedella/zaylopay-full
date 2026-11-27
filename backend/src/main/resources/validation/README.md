# Disposable-domain dataset

The bundled `disposable-email-domains.conf` snapshot comes from the community-maintained
[disposable-email-domains repository](https://github.com/disposable-email-domains/disposable-email-domains).
Its README grants use under [CC0](https://creativecommons.org/publicdomain/zero/1.0/).
The adjacent metadata records the exact upstream commit, SHA-256 and retrieval date.

Updates are deliberate: review an upstream revision, download its plain-text blocklist using the
commit-pinned raw URL, validate the changed domains, update the metadata checksum and rerun
`EmailContactPolicyTest` plus the shared frontend cases. Restart the backend to load a new snapshot.
The application neither executes downloaded content nor fetches a new list during startup/requests.

Deployments may override `app.email-validation.disposable-resource` with a reviewed `file:` resource.
Remote resource URLs are rejected. Matching covers complete domain labels and their subdomains;
it does not use substring matching. Known-domain blocking has false positives and incomplete coverage,
and never proves whether an individual mailbox is temporary.
