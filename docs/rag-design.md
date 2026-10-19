# RAG Design: Document Q&A for the Chatbot

This document explains the design of the Document Q&A feature in the
`com.kk.pde.ds.rag` bundle. It gives the decisions and the problems that we found
during the work. For the configuration properties and the verification record, see
[rag-reference.md](rag-reference.md).

## Summary

The chatbot can read your local documents and answer questions from them. It accepts
these formats:

- `.pdf`
- `.docx` and `.pptx`
- `.html` and `.htm`
- `.txt` and `.md`
- image files `.png`, `.jpg`, `.jpeg`, `.bmp`, `.tif`, `.tiff`, and `.gif`, through OCR

When you ask a question, the chatbot finds the relevant passages and answers from
them. The answer includes a citation.

## Search as a tool

A plain LLM answers from the data that it learned during training. RAG
(Retrieval-Augmented Generation) adds a step: before the model answers, it searches
your documents.

This feature gives the search to the model as a tool. It does not always add the
documents to the prompt. The tool name is `document_search`. The model decides when to
call it, the same as for the `calculator` and `http_fetch` tools. The model already
knew how to call tools. Thus the feature adds only one more tool.

## Services

Each step is an OSGi service behind an interface in `com.kk.pde.ds.rag.api`. You can
replace each service and the callers do not change.

| Service | Job |
|---------|-----|
| `DocumentParser` (`MultiFormatDocumentParser`) | Converts a file to plain text. |
| `OcrEngine` (`TesseractCliOcrEngine`) | Reads text from images. `MultiFormatDocumentParser` references this service. |
| `TextChunker` (`SlidingWindowChunker`) | Divides text into overlapping chunks of approximately 400 tokens. |
| `EmbeddingClient` (`OpenAiEmbeddingClient`) | Converts each chunk to a vector of numbers. |
| `VectorStore` (`InMemoryVectorStore`) | Stores the vectors and finds the nearest vectors. |
| `DocumentIngestionService` | Runs parse → chunk → embed → store, and runs searches. |
| `DocumentSearchTool` / `IngestDocumentsTool` | The two tools that the LLM can call. |

The sequence for a question:

```
"What does the report say about latency?"
        │  (the model decides to call document_search)
        ▼
embed the question  →  find the nearest chunks (cosine similarity)  →
   return them with citations  →  the model answers from them
```

## Important design points

### Embeddings are coordinates for meaning

An embedding converts a text such as "the cat sat" into approximately 1024 numbers.
These numbers are a point in a vector space. Texts with a similar meaning have points
near each other. Thus a search for relevant text becomes a search for the nearest
points. The search measures the angle between vectors (cosine similarity).

`InMemoryVectorStore` does a brute-force search over `float[]`. This is very simple.
The store is behind an interface, so a real vector database (pgvector) can replace it.
The callers do not change.

### The embedding model needs role prefixes (E5)

The default model is `intfloat/multilingual-e5-large`. This model expects a label on
each text. The label is `"query: "` for a question and `"passage: "` for a stored
document. Without the labels, the retrieval quality decreases, and no error occurs.

`EmbeddingClient` adds the labels automatically. If you use a model that does not use
the labels, for example `qwen3-embedding`, you can set the labels to empty values.

### Chunk size

A large chunk hides the relevant fact in too much other text. E5 also truncates text
after 512 tokens without a warning, so a large chunk can lose half of its text. A
small chunk loses context.

The chunker targets approximately 400 tokens with approximately 12% overlap. It
divides the text at paragraph boundaries first, then at sentence boundaries. Thus it
does not divide a sentence. The overlap is a safety margin: a fact that crosses a
boundary is in both chunks.

## Why we did not use Apache Tika

The first plan used **Apache Tika**, a general-purpose text extraction library. Tika
reads all the formats with one library. The spec identified "Tika in OSGi" as the
main risk. That was correct, but the cause was not the expected cause.

The problem came from transitive dependencies. This is the sequence of failures:

1. We added the Tika OSGi uber-bundle. The build failed. The bundle does not export
   its API. It imports `tika-core` and registers parsers as OSGi services. We added
   `tika-core` and used the services. Lesson: check what a bundle exports. Do not
   trust its description.
2. The next failure: `tika-core` requires **slf4j 2.0**. This project uses
   **slf4j 1.7**.
3. We tried an upgrade from Logback 1.2 to Logback 1.3, the slf4j 2.0 line. A new
   failure occurred. slf4j 2.0 in OSGi needs the **Aries SPI Fly** ServiceLoader
   mediator. We added it.
4. Then the real problem occurred. The slf4j 2.0 upgrade **broke Felix Health Check**.
   Felix Health Check imports `org.slf4j.helpers [1.7,2.0)`. Version 2.0 is outside
   that range. Thus one library made the logging of the full platform move to a new
   major version, with an unknown effect.

This is a transitive constraint cascade. One dependency moves a basic package to a new
major version. Then each bundle that imports that package with a strict upper bound
cannot resolve. Our own bundles did not fail. They used open minimum versions such as
`1.7.32`, which means `[1.7.32, ∞)`. The `[1.7,2.0)` range in Felix Health Check
blocked the build.

We had two options: migrate the full logging stack for one library, or avoid that
library. We avoided the library:

- **PDF:** Apache **PDFBox 2.0.x**. PDFBox logs through `commons-logging` (JCL), not
  slf4j. Thus it does not conflict with the existing stack.
- **`.docx` and `.pptx`:** these files are zip files that contain XML. The parser
  reads the zip and extracts the text from `word/document.xml` and
  `ppt/slides/slideN.xml`. This needs no Apache POI and no dependency tree. The code
  was approximately 120 lines.
- **`.html` and `.txt`:** the parser removes the tags or reads the bytes.

The result was **three new bundles** (pdfbox, fontbox, commons-logging). The platform
logging did not change. The existing slf4j 1.7 / Logback / Felix Health Check stack
did not change.

Tika is a good library. The lesson is different: the resolver is the cheapest place to
find a dependency conflict. When a conflict cascades, examine the requirement before
you accept the cost.

## OCR

Some documents contain text as pixels, not as a text layer. The OCR path reads that
text.

- **Scanned PDF pages:** if the text layer of a page has fewer characters than
  `rag.ocr.min.chars.per.page`, the parser treats the page as a scan. PDFBox
  `PDFRenderer` renders the page as an image (default 300 DPI), and the OCR engine
  reads the full page.
- **Images on text pages:** on a PDF page with a text layer, the parser keeps the text.
  Then it sends each embedded image to OCR and adds the result.
- **`.docx` images:** the parser sends each image in `word/media/` to OCR and adds the
  result.
- **`.pptx` images:** not supported yet.

OCR is the only step that starts an external process. `TesseractCliOcrEngine` runs
this command:

```
tesseract <img> stdout -l <lang>
```

If the `tesseract` binary is not available, or if `rag.ocr.enabled=false`, ingestion
continues with text only. No error occurs.

### Why the Tesseract CLI, not Tess4J

The CLI subprocess adds no Java or OSGi dependencies. It also adds no slf4j 2.x
transitive dependency. This is the same reason that we did not use Tika. The cost is
an external binary on the host.

PDFBox 2.0.32 already contains `PDFRenderer`, so the rasterization needs no new
dependency.

## The `-D` option that had no effect

At first, the startup auto-ingest did not start. The cause was the position of the
user options in `run.sh`. The script put them after `-jar`:

```bash
java -jar osgi.jar -configuration ... "$@"     # -Drag.docs.dir is here = IGNORED
```

The command form is `java [JVM-options] -jar app.jar [program-args]`. The JVM gives
all arguments after the jar to the application, not to the JVM. Thus Equinox received
`-Drag.docs.dir=...` as a launcher argument, and the JVM did not set the system
property.

We moved `"$@"` before `-jar`. This change also corrected the documented
`-Dopenrouter.api.key` option, which had the same latent problem. Lesson: the order of
the arguments is important.

## Manifest-first OSGi: the XML controls the component

This project is a Tycho manifest-first build. Developers write the `OSGI-INF/*.xml`
component descriptors manually and keep them in Git. Felix SCR reads only the XML. The
`@Component` annotations are documentation for people.

Thus, when a component must run its `@Activate` method, the XML must contain
`activate="activate"`. In this feature, `activate` reads the configuration and starts
the auto-ingest. If the XML does not have the attribute, the method does not run. No
error occurs, and the annotation gives incorrect information. Lesson: in
manifest-first OSGi, the XML controls the component.

## Run the feature

1. Build the project:
   ```bash
   mvn clean verify
   ```
2. Set the API key:
   ```bash
   export OPENROUTER_API_KEY=your_key
   ```
3. Start the product with an embeddings endpoint and a document folder:
   ```bash
   ./distribution/scripts/run.sh \
       -Dopenrouter.base.url=https://your-embeddings-proxy/v1 \
       -Drag.docs.dir=/path/to/your/documents
   ```
4. Make sure that the log shows
   `Auto-ingest complete: Ingested N file(s), 0 failed, M chunk(s)`.
5. Ask the chatbot a question about your documents. The answer gives its sources.

For the full configuration and the verification record, see
[rag-reference.md](rag-reference.md).

## Conclusion

Two ideas are important:

1. **RAG means "search before you answer."** Embeddings change the search into a
   geometry problem. The other parts are infrastructure behind clear interfaces.
2. **A dependency is a negotiation, not only a download.** One library tried to move
   the full platform from slf4j 1.7 to 2.0. The correct response was to measure the
   effect and find a smaller solution. Here, the solution was to parse the formats
   directly, not to migrate the logging stack for one library.
