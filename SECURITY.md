# Security Notes

This project is an **educational OSGi showcase. It is for use on `localhost` only.** Its HTTP
surface (Felix Jetty on port 8080) has no authentication by design, so that you can try the demo
with no setup. This is acceptable on a trusted single-user machine. It is **not safe on a
network**. This document records the known exposures. It also tells you what to do before other
people can connect to any part of it.

The build already includes the low-cost defences that are always useful. Every servlet has a
limit on the request-body size, so a single large POST cannot cause an out-of-memory error in the
shared Jetty. This document records all other items below, but the code does not implement them.
This is by design.

## Known exposures (localhost-only assumptions)

| # | Surface | Exposure |
|---|---------|----------|
| 1 | `POST /mcp`, `POST /llm/chat` | **No authentication and no `Origin`/CSRF check.** Any client that can connect to port 8080 can call every registered MCP tool and use `/llm/chat`. This includes a malicious web page in a browser on the same machine, through a plain form POST. Each `/llm/chat` call uses your OpenRouter credits. |
| 2 | `ingest_documents` MCP tool | The tool accepts **any absolute filesystem path**. There is no allowlist root. With #1, a remote caller can ingest any readable file (`/Users/you/Documents`, …). The caller can then read the file contents through `document_search`. This gives an arbitrary-file-read primitive. |
| 3 | `http_fetch` MCP tool | The tool checks only the URL scheme. It sends requests to **internal and link-local addresses** (`http://169.254.169.254/…`, `http://localhost:8080/system/console`), and it follows redirects. This gives an SSRF primitive. An attacker can use it through #1, or through prompt injection of the LLM. |
| 4 | `settings/chatbot.properties` | The user can type an API key in the Swing **Settings** dialog and save it. The dialog then writes the key to this file in **plaintext**, with default permissions. The dialog shows the saved key again in the password field. A key from an environment variable or from `-D` does not go into the file: the dialog shows only saved values. |
| 5 | Logging | The code logs chat prompts, LLM responses and **retrieved document text** at `INFO` (the default level). Thus document contents can go into `consoleLog` and the log files. The code does not log API keys. |
| 6 | Felix WebConsole | The WebConsole has the well-known demo credentials `admin`/`admin` (`distribution/configuration/config.ini`). |

## Before you expose this outside localhost

Before you bind to a non-loopback interface, do all of these steps:

1. **Add authentication to the HTTP surface.** Require a shared-secret header on `/mcp` and
   `/llm/chat`. Alternatively, use the basic-auth approach of the WebConsole.
2. Reject content types other than `application/json` on `/mcp` and `/llm/chat`.
3. Add an `Origin` allowlist to prevent browser CSRF.
4. **Limit `ingest_documents`** to a configured allowlist root. Resolve the real path. Make sure
   that the real path starts with the allowed directory.
5. Make the "path does not exist" errors less specific. An attacker must not use them to find
   which files exist.
6. **Limit `http_fetch`.** Resolve the host. **Block the private, loopback and link-local ranges**
   (10/8, 172.16/12, 192.168/16, 127/8, 169.254/16, `::1`, fc00::/7).
7. Disable redirects in `http_fetch` (`setInstanceFollowRedirects(false)`).
8. Do not save keys that come from an environment variable or from `-D`. The code does this now: the **Settings** dialog shows and saves only the values in the file.
9. When you save a key that the user typed, set file permissions for the owner only. In the
   dialog, show a placeholder, not the saved key.
10. **Change the log level of content logging** (chat text, retrieved documents) to `DEBUG`.
11. **Change the WebConsole credentials** from `admin`/`admin`. Alternatively, remove the
    WebConsole from the product.

The related code is in these bundles:

- `com.kk.pde.ds.mcp.server` (servlet + tools)
- `com.kk.pde.ds.mcp.llm` (agent + `/llm/chat`)
- `com.kk.pde.ds.chatbot` (`ChatConfig`, `SettingsDialog`)
- `com.kk.pde.ds.rag` (ingestion)
