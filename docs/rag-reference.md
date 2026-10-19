# RAG Reference: Document Q&A Configuration

The `com.kk.pde.ds.rag` bundle adds retrieval-augmented generation (RAG) to the
chatbot. The bundle ingests local documents. The LLM retrieves relevant passages through a
tool (search-as-tool). The existing chat/tool loop decides when to call the tool. The
bundle has no always-on inject-then-answer pipeline.

For the design and the decisions, see [rag-design.md](rag-design.md).

## Components

```
DocumentSearchTool (IMcpTool "document_search")  ← the LLM's retrieval entry point
        │
DocumentIngestionService  — parse → chunk → embed → store, and search
   ├─ DocumentParser  (MultiFormatDocumentParser)
   │     └─ OcrEngine (TesseractCliOcrEngine → tesseract subprocess)
   ├─ TextChunker     (SlidingWindowChunker)
   ├─ EmbeddingClient (OpenAiEmbeddingClient → /v1/embeddings)
   └─ VectorStore  ← InMemoryVectorStore (brute-force cosine over float[])
IngestDocumentsTool (IMcpTool "ingest_documents")  ← ingest a folder on demand
```

Each collaborator is an OSGi service behind an interface in `com.kk.pde.ds.rag.api`.
You can replace each service, for example `VectorStore` with pgvector. The callers do
not change.

The bundle has 8 DS components. The `OSGI-INF/*.xml` descriptors declare them.

## Supported formats

| Format | Extraction method |
|--------|-------------------|
| `.pdf` | Apache PDFBox 2.0.x |
| `.docx`, `.pptx` | The parser reads the OOXML zip directly. No Apache POI. No extra dependencies. |
| `.html`, `.htm` | The parser removes the tags and decodes the entities. |
| `.txt`, `.md` | The parser reads the file as UTF-8. |
| `.png`, `.jpg`, `.jpeg`, `.tif`, `.tiff`, `.bmp`, `.gif` | OCR of the full image. If OCR is not available, the parser skips the file and logs a warning. |

## Configuration

Set each value as a JVM system property (`-D<property>=<value>`). Some values also
accept an environment variable. A system property has priority over the environment
variable.

The embeddings client uses the same `openrouter.*` properties as the chat client.
Thus the embeddings endpoint uses the same base URL and API key.

| Property | Env var | Default | Purpose |
|----------|---------|---------|---------|
| `openrouter.api.key` | `OPENROUTER_API_KEY` | none | API key. Required. |
| `openrouter.base.url` | none | `https://openrouter.ai/api/v1` | The client sends embeddings requests to `{base}/embeddings`. |
| `openrouter.embeddings.model` | `OPENROUTER_EMBEDDINGS_MODEL` | `intfloat/multilingual-e5-large` | Embedding model. |
| `rag.docs.dir` | none | none | Folder to ingest at startup. The ingestion includes subfolders. |
| `rag.embedding.query.prefix` | none | `query: ` | E5 query role prefix. Set it to an empty value for a model that is not E5. |
| `rag.embedding.passage.prefix` | none | `passage: ` | E5 passage role prefix. |
| `rag.chunk.target.tokens` | none | `400` | Chunk size. Keep it below the 512-token E5 window. |
| `rag.chunk.overlap.tokens` | none | `50` | Chunk overlap (approximately 12%). |
| `rag.chunk.chars.per.token` | none | `4` | Characters per token. The chunker uses this ratio to estimate token counts. |
| `rag.ocr.enabled` | `RAG_OCR_ENABLED` | `true` | Set to `false` to disable OCR. |
| `rag.ocr.binary` | none | `tesseract` | Name or path of the Tesseract binary. |
| `rag.ocr.lang` | none | `eng` | Tesseract `-l` value, for example `eng+tur`. |
| `rag.ocr.timeout.seconds` | none | `60` | Time limit for the subprocess, for each image. |
| `rag.ocr.dpi` | none | `300` | Resolution for the rasterization of scanned PDF pages. |
| `rag.ocr.min.chars.per.page` | none | `16` | If a PDF page has fewer text-layer characters than this value, the parser treats the page as a scan. |
| `rag.ocr.max.images.per.doc` | none | `200` | Maximum number of embedded images to OCR in one document. |

> **E5 note:** `multilingual-e5-large` is asymmetric and sensitive to prefixes. The client
> adds `query: ` to queries and `passage: ` to passages. The model also truncates text
> after 512 tokens, so the default chunk target is 400. To use `qwen3-embedding-8B`, set the
> model and set both prefixes to empty values.

## OCR behavior

OCR is on by default when a `tesseract` binary is available on the host.

- If the text layer of a PDF page has fewer characters than
  `rag.ocr.min.chars.per.page`, the parser treats the page as a scan. PDFBox
  `PDFRenderer` renders the page at `rag.ocr.dpi`. Then the OCR engine reads the full
  page.
- On a PDF page with a text layer, the parser keeps the text. Then it sends each
  embedded image to OCR and adds the result.
- The parser sends each `.docx` image in `word/media/` to OCR and adds the result.
- The parser does not send `.pptx` images to OCR yet.

`TesseractCliOcrEngine` runs `tesseract <img> stdout -l <lang>` as a subprocess. OCR is
the only step that starts an external process. If the binary is not available, or if
`rag.ocr.enabled=false`, ingestion continues with text only.

For server runs, set `-Djava.awt.headless=true`. PDFBox and ImageIO use AWT.

## Run the feature

1. Build the p2 repository and the product:
   ```bash
   mvn clean verify
   ```
2. Set the API key:
   ```bash
   export OPENROUTER_API_KEY=your_key
   ```
3. Start the product:
   ```bash
   ./distribution/scripts/run.sh \
       -Dopenrouter.base.url=https://your-proxy/v1 \
       -Dopenrouter.embeddings.model=intfloat/multilingual-e5-large \
       -Drag.docs.dir=/path/to/your/documents
   ```
4. Make sure that the log shows each component activation and these lines:
   ```
   DocumentIngestionService - Auto-ingesting documents from /path/to/your/documents
   DocumentIngestionService - Auto-ingest complete: Ingested N file(s), 0 failed, M chunk(s) added.
   ```
5. In the chatbot, ask a question about your documents. The model calls
   `document_search`. The answer includes citations such as `report.pdf (chunk 3)`.

## MCP tools

The two tools register through `IMcpToolRegistry`. You can call them from the chatbot
or send JSON-RPC requests to the MCP server at `http://localhost:8080/mcp`.

| Tool | Argument | Required | Default | Description |
|------|----------|----------|---------|-------------|
| `ingest_documents` | `path` | yes | none | A file or a folder to ingest. |
| `document_search` | `query` | yes | none | The search text. |
| `document_search` | `top_k` | no | `5` | Number of passages to return. A value outside 1 to 20 gives the default. |

To ingest a folder, use this request:

```bash
curl -s -X POST http://localhost:8080/mcp -H 'Content-Type: application/json' -d \
 '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"ingest_documents","arguments":{"path":"/path/to/docs"}}}'
```

To search the documents, use this request:

```bash
curl -s -X POST http://localhost:8080/mcp -H 'Content-Type: application/json' -d \
 '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"document_search","arguments":{"query":"your question","top_k":"5"}}}'
```

For the general MCP test procedure, see [mcp-server-testing.md](mcp-server-testing.md).

## Network access

The embeddings client is the only RAG component that makes network calls. It calls the
configured embeddings endpoint.

The default `openrouter.base.url` is `https://openrouter.ai/api/v1`, a public internet
address. For an airgapped installation, set `openrouter.base.url` to an internal
endpoint. Then the RAG components do not connect to the public internet.

Other bundles can also connect to the network. For example, the `http_fetch` MCP tool
in `com.kk.pde.ds.mcp.server` can send HTTP requests to URLs. For the known exposures, see
[SECURITY.md](../SECURITY.md).

`run.sh` and `run.bat` set `-Djavax.xml.accessExternalDTD=`,
`-Djavax.xml.accessExternalSchema=`, and `-Djavax.xml.accessExternalStylesheet=`. These
options prevent external DTD and schema access during XML parsing.

## Why not Apache Tika

Tika was the first plan. Each Tika release requires **slf4j 2.0**. slf4j 2.0 conflicts
with the slf4j 1.7 / Logback 1.2 stack of this project. It also breaks Felix Health
Check, which imports `org.slf4j.helpers [1.7,2.0)`.

A Tika upgrade would need a migration of the full runtime logging: Logback 1.3, Aries
SPI Fly, and Felix Health Check upgrades. We did not do that migration. The parser
extracts the text directly:

- PDFBox for PDF. PDFBox logs through commons-logging, not slf4j.
- A small OOXML zip reader for the Office formats.

The existing stack did not change.

## Why not firecrawl/pdf-inspector

We examined <https://github.com/firecrawl/pdf-inspector> (v0.2.6) on 2026-08-02. It is
not an OCR replacement. Its README says that it converts PDFs to Markdown "all without
OCR." It is the classifier that decides which pages need OCR. The Firecrawl OCR is the
proprietary GPU service behind their hosted `/parse`. That service is not part of the
open-source code. Thus pdf-inspector cannot replace `TesseractCliOcrEngine`.

It is also not useful for the job that it does. It has no JVM binding. It supports
only Rust, Python, Node, and WASM. Thus an integration must start its `detect-pdf` CLI
as a subprocess. That is the same subprocess pattern that we use now, but for a second
external binary. The project supplies no prebuilt binaries (`cargo install
pdf-inspector`). Thus the host would need a Rust toolchain. That is worse than
`brew install tesseract`.

Also, pdf-inspector would replace **PDFBox**, not Tesseract. But PDFBox must stay for
two reasons:

- PDFBox logs through commons-logging (see above).
- `PDFRenderer` does the page rasterization. pdf-inspector cannot do it.

One idea from pdf-inspector can be useful later. It classifies pages by samples of the
content streams. That method is more precise than our character-count rule
(`rag.ocr.min.chars.per.page`, see `extractPdfWithOcr` in
[`MultiFormatDocumentParser`](../com.kk.pde.ds.rag/src/com/kk/pde/ds/rag/internal/MultiFormatDocumentParser.java)). Our rule can classify a page as a scan when the
page has a small but real text layer, for example a chart with a short caption. Then
the parser rasterizes that page at 300 DPI without need. The result is extra work, not
incorrect output. Thus it is an idea for later, not a bug. Examine the repository again
if it adds a JVM binding.

## Verification record

The first verification occurred on 2026-06-12, with commit `f7dcbf3`. That commit added
the RAG bundle. The OCR engine came later, in commit `27e7bfa` on 2026-07-08.

- **Build:** `mvn clean verify` passed. This included the full Tycho reactor and the
  product assembly.
- **Runtime wiring:** we started the product. All the DS components activated (seven
  at that time). Both tools registered and appeared in the MCP `tools/list`. Logging
  (Logback 1.2.11) did not change.
- **Pipeline logic (offline):** the parser extracted `.txt`, `.html`, and `.docx`
  correctly. The chunker made overlapping chunks with citations. The cosine ranking in
  `InMemoryVectorStore` was correct.
- **End-to-end embed and retrieve:** we used a mock OpenAI-compatible
  `/v1/embeddings` server. Auto-ingest reported
  `Ingested 3 file(s), 0 failed, 3 chunk(s)`. `document_search` returned the passages
  with citations and scores.
- **Not verified:** the semantic ranking quality with the real
  `multilingual-e5-large` endpoint. That test needs the airgapped corporate proxy. The
  test used only a character-frequency mock, so it examined only the infrastructure.
- **Not verified at runtime:** the current count of 8 DS components. That count comes
  from the `OSGI-INF/*.xml` descriptors in the source, not from a runtime check.
