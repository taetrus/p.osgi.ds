# FOR Kerem — Teaching the Chatbot to Read Your Documents (RAG), Explained Like a Story

## The one-sentence idea

We gave the chatbot a **library card**: it can now read your local documents
(`.pdf`, `.docx`, `.pptx`, `.html`, `.txt`), and when you ask a question, it walks
to the right shelf, pulls the relevant passages, and answers using *them* — with a
citation, like a good student.

## The mental model: a librarian, not a know-it-all

A plain LLM is a confident person answering from memory. RAG (Retrieval-Augmented
Generation) turns it into a **librarian**: instead of guessing, it looks things up.

The trick we used is **search-as-a-tool**. We didn't bolt on a rigid "always stuff
the documents into the prompt" pipeline. We handed the model a tool called
`document_search` and let *it* decide when to reach for it — exactly like the
existing `calculator` or `http_fetch` tools. The model already knew how to call
tools; we just added a new book to the shelf of things it can do.

## The cast of characters (all OSGi services, all swappable)

Every step is a service hiding behind an interface in `com.kk.pde.ds.rag.api`, so
you can replace any one of them later without anyone noticing:

| Service | Job | The analogy |
|---------|-----|-------------|
| `DocumentParser` | turn a file into plain text | the **scanner** |
| `TextChunker` | cut text into ~400-token overlapping pieces | the **scissors** |
| `EmbeddingClient` | turn each piece into a vector of numbers | the **translator** (text → coordinates) |
| `VectorStore` | store vectors, find the nearest ones | the **card catalog** |
| `DocumentIngestionService` | run parse→chunk→embed→store, and search | the **head librarian** |
| `DocumentSearchTool` / `IngestDocumentsTool` | the two buttons the LLM can press | the **front desk** |

The flow when you ask a question:

```
"What does the report say about latency?"
        │  (the model decides to call document_search)
        ▼
embed the question  →  find the nearest chunks (cosine similarity)  →
   hand them back with citations  →  the model answers grounded in them
```

## The genuinely clever bits

**1. Embeddings are coordinates for meaning.** An embedding turns "the cat sat" into
a list of ~1024 numbers — a point in space. Sentences with similar meaning land
near each other. "Find relevant text" becomes "find the nearest points," which is
just measuring angles between vectors (cosine similarity). Our `InMemoryVectorStore`
does this brute-force over `float[]` — gloriously simple, and behind an interface so
a real vector database (pgvector) can replace it later without touching a single
caller.

**2. The embedding model has table manners (E5 prefixes).** Our default model,
`intfloat/multilingual-e5-large`, was *trained* expecting you to label your text:
prepend `"query: "` to questions and `"passage: "` to stored documents. Skip the
labels and retrieval quality quietly drops. So `EmbeddingClient` adds them
automatically — and lets you blank them out if you switch to a model (like
`qwen3-embedding`) that doesn't want them.

**3. Chunk size is a Goldilocks problem.** Too big and you bury the needle in hay
(and E5 silently *truncates* anything past 512 tokens — you'd lose half each chunk);
too small and you lose context. We target ~400 tokens with ~12% overlap, splitting
on paragraph then sentence boundaries so we never cut mid-thought. The overlap is
the safety net: a fact that straddles a boundary survives in both chunks.

## The plot twist: the Apache Tika saga (the best lesson here)

The plan was to use **Apache Tika** — the Swiss Army knife of text extraction. It
handles every format under one roof. The spec even flagged "Tika in OSGi is the main
risk." It was right, but not for the reason anyone expected.

Here's the detective story, because it's a perfect example of how *transitive
dependencies* can ambush you:

1. We added Tika's OSGi uber-bundle. The build failed: it doesn't actually *export*
   its API — it imports `tika-core` and registers parsers as OSGi services. Fine,
   we added `tika-core` and consumed the services. (Lesson: **verify what a bundle
   exports; don't trust the marketing.**)
2. Next failure: `tika-core` demands **slf4j 2.0**. Our project runs **slf4j 1.7**.
3. We tried upgrading: Logback 1.2 → 1.3 (the slf4j-2.0 line). New failure — slf4j
   2.0 in OSGi needs an **Aries SPI Fly** ServiceLoader mediator. Added it.
4. *Then* the real wall: upgrading slf4j to 2.0 **broke Felix Health Check**, which
   pins `org.slf4j.helpers [1.7,2.0)` — an upper-bounded import that 2.0 can't
   satisfy. One library wanted to drag the whole platform's logging into a new major
   version, with an unknown blast radius.

This is a **transitive constraint cascade**: one dependency pins a foundational
package to a new major version, and every bundle that declared a strict
upper-bounded import of it becomes unsatisfiable at once. (Our own bundles survived —
they used open minimums like `1.7.32` = `[1.7.32, ∞)`. Felix Health Check's
`[1.7,2.0)` is what blocked everything.)

So we stopped and asked: migrate the entire logging stack to host one library, or
sidestep it? We **sidestepped**:

- **PDF** → Apache **PDFBox 2.0.x**, which logs via `commons-logging` (JCL), *not*
  slf4j. Zero conflict with the existing stack.
- **`.docx` / `.pptx`** → these are just **zip files full of XML**. We read the zip
  and pull the text out of `word/document.xml` / `ppt/slides/slideN.xml` directly —
  no Apache POI, no dependency tree, ~120 lines of honest code.
- **`.html` / `.txt`** → strip tags / read bytes. Nothing exotic.

The result: **three new bundles** (pdfbox, fontbox, commons-logging) instead of a
platform-wide logging migration. The existing slf4j 1.7 / Logback / Felix Health
Check stack was never touched.

The lesson isn't "Tika is bad" — it's excellent. The lesson is **the cheapest place
to discover a dependency conflict is the resolver, and the right response to a
cascade is to question the requirement, not to keep paying for it.**

## A second, smaller gotcha (the `-D` that did nothing)

The startup auto-ingest didn't fire at first. The cause: `run.sh` passed user flags
*after* `-jar`:

```bash
java -jar osgi.jar -configuration ... "$@"     # -Drag.docs.dir lands here = IGNORED
```

In `java [JVM-options] -jar app.jar [program-args]`, anything after the jar goes to
the *application*, not the JVM — so `-Drag.docs.dir=...` was silently handed to
Equinox as a launcher argument and never became a system property. Moving `"$@"`
*before* `-jar` fixed it (and fixed the documented `-Dopenrouter.api.key` usage,
which had the same latent bug). **Argument order is not cosmetic.**

## A manifest-first OSGi reminder

This project is **Tycho manifest-first**: the `OSGI-INF/*.xml` service descriptors
are hand-written and checked in. The `@Component` annotations are documentation for
humans — Felix SCR only reads the XML. So when a component needs its `@Activate`
method to actually run (ours reads config and kicks off auto-ingest), the XML must
say `activate="activate"`. Forget it and the annotation lies to you while nothing
happens. **In manifest-first OSGi, the XML wins.**

## How to run it

```bash
mvn clean verify
export OPENROUTER_API_KEY=your_key
./distribution/scripts/run.sh \
    -Dopenrouter.base.url=https://your-embeddings-proxy/v1 \
    -Drag.docs.dir=/path/to/your/documents
```

Watch the log say `Auto-ingest complete: Ingested N file(s), 0 failed, M chunk(s)`,
then ask the chatbot a question about your documents. It'll cite its sources.

Full configuration and the verification record live in
`com.kk.pde.ds.rag/README.md`.

## The takeaway

Two ideas worth keeping:

1. **RAG is just "look it up before you answer."** Embeddings make "look it up" a
   geometry problem; everything else is plumbing behind clean interfaces.
2. **A dependency is a negotiation, not a download.** When one library tries to
   renegotiate your whole platform (slf4j 1.7 → 2.0), the senior move is to weigh
   the blast radius and find the smaller door — here, parsing formats directly
   instead of migrating the logging stack to host one toolkit.
