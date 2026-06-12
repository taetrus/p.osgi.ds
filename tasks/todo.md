# RAG document Q&A for the chatbot

Spec: Obsidian `Inbox/p.osgi.ds RAG support.md`. Search-as-tool retrieval grounded in
local documents (.pdf/.docx/.pptx/.txt/.html), airgapped-friendly.

## Approved decisions
- Tika: `org.apache.tika:tika-bundle-standard:3.3.1` (OSGi uber-bundle).
- Ingestion: auto-scan `-Drag.docs.dir` at startup + `ingest_documents` MCP tool.
- Embeddings default: `intfloat/multilingual-e5-large` (overridable). e5 role prefixes applied.
- One new bundle `com.kk.pde.ds.rag`, exports `…rag.api` (interfaces), impls internal.

## Plan
- [x] 1. Add `tika-bundle-standard:3.3.1` Maven location to the `.target`.
- [ ] 2. Scaffold module `com.kk.pde.ds.rag` (pom, build.properties, MANIFEST, OSGI-INF/).
- [ ] 3. API package: `Chunk`, `ScoredChunk`, `DocumentParser`, `TextChunker`,
        `EmbeddingClient`, `VectorStore`, `DocumentIngestionService`.
- [ ] 4. `Json` util (tiny recursive-descent parser/writer for embeddings I/O).
- [ ] 5. `TikaDocumentParser` (AutoDetectParser, BodyContentHandler, offline-safe).
- [ ] 6. `SlidingWindowChunker` (~400 tok target, ~12% overlap, para/sentence boundaries).
- [ ] 7. `OpenAiEmbeddingClient` (HttpURLConnection → {baseUrl}/embeddings, e5 prefixes).
- [ ] 8. `InMemoryVectorStore` (brute-force cosine over float[]).
- [ ] 9. `DocumentIngestionServiceImpl` (parse→chunk→embed→store; search; startup auto-scan).
- [ ] 10. Tools: `DocumentSearchTool` + `IngestDocumentsTool` (IMcpTool).
- [ ] 11. Hand-write each `OSGI-INF/*.xml` SCR descriptor.
- [ ] 12. Wire module into root pom, feature.xml, p2.product; offline XML flags in run scripts.
- [ ] 13. `mvn clean verify` green; document manual e2e.

## PIVOT (user decision): drop Tika, parse directly — keep slf4j 1.7 stack
- Tika (any version) hard-requires slf4j 2.0, which cascaded into logback 1.3 + SPI Fly +
  breaking Felix Health Check (slf4j.helpers pinned [1.7,2.0)). User chose to avoid the churn.
- Reverted: logback back to 1.2.11; removed SPI Fly + Tika from target/product.
- New parser `MultiFormatDocumentParser`:
  - .pdf  -> Apache PDFBox 2.0.32 (+ fontbox 2.0.32, + commons-logging 1.3.5 — JCL, not slf4j).
  - .docx/.pptx -> read OOXML zip directly, strip XML (no POI, zero extra deps).
  - .html/.htm -> tag strip + entity decode;  .txt/.md -> UTF-8.
- Three new bundles only (pdfbox, fontbox, commons-logging); slf4j/logback/Felix HC untouched.

## Earlier Tika detour (superseded by the pivot above)
- tika-bundle-standard exports only `org.apache.tika.parser.internal`; it IMPORTS tika-core
  API + registers DefaultParser/DefaultDetector as OSGi services via its Activator.
  → Added tika-core 3.3.1; TikaDocumentParser now @References Parser+Detector and wraps in
    AutoDetectParser (no `new AutoDetectParser()` — that hits the ServiceLoader/classloader trap).
- Tika (3.3.1 AND 2.9.4) hard-requires slf4j [2.0,3). Project had slf4j 1.7.32 / logback 1.2.11.
  → Bumped logback to 1.3.14 (Java-8 line on slf4j 2.0).
- slf4j 2.0 + logback 1.3 need an osgi.serviceloader extender (processor/registrar).
  → Added Apache Aries SPI Fly 1.3.7 (self-contained, embeds ASM); started at level 1 so its
    weaving hook is live before anything logs; logback started at level 1 for the registrar.

## Review — DONE
New bundle `com.kk.pde.ds.rag`: search-as-tool RAG over local docs, wired into the
existing IMcpTool registry. No changes to existing modules' code.

Pipeline: MultiFormatDocumentParser (PDFBox for PDF; direct OOXML-zip for docx/pptx;
tag-strip for html; UTF-8 for txt/md) → SlidingWindowChunker (~400 tok, ~12% overlap,
para/sentence boundaries) → OpenAiEmbeddingClient (/v1/embeddings, mirrors chat-client
config, E5 query/passage prefixes) → InMemoryVectorStore (brute-force cosine, behind
swappable VectorStore iface). Two tools: document_search, ingest_documents. Auto-scan
via -Drag.docs.dir at startup (daemon thread).

Deps added: pdfbox 2.0.32 + fontbox 2.0.32 + commons-logging 1.3.5 (JCL, not slf4j —
existing slf4j 1.7 / Logback 1.2 / Felix Health Check untouched). Tika abandoned after
it forced a project-wide slf4j-2.0 migration (user decision).

Also fixed: run.sh/run.bat passed -D flags AFTER -jar (ignored by JVM) → moved before
-jar. Offline XML-access flags added for the airgap.

Verified:
- mvn clean verify: full reactor + product assembly GREEN.
- Runtime: all 7 DS components activate; document_search + ingest_documents register
  and appear in MCP tools/list; Logback logging not regressed (no SLF4J NOP).
- Offline logic: parser (txt/html/docx), chunker overlap+citations, cosine ranking.
- End-to-end vs mock /v1/embeddings: auto-ingest "Ingested 3 file(s), 0 failed, 3
  chunk(s)"; document_search returned passages with citations + scores.
- Graceful no-API-key handling.
- NOT verified (env limit): semantic ranking quality with the real e5/qwen3 endpoint.
