# ZayloPe frontend

Use the [root README](../README.md) for the current architecture, prerequisites, environment and team workflow.

From this directory: `npm ci`, `npm run dev`, `npm run lint`, `npm run build`. Vite serves the existing SPA on port 5173. Public configuration examples are in `.env.example`; backend secrets must never be exposed with a `VITE_` prefix.

See [QA README](qa/README.md) for the actual Node/browser test commands, fixture requirements and private artifact output. There is no `npm test` script. Existing feature/component architecture and approved UI are unchanged.
