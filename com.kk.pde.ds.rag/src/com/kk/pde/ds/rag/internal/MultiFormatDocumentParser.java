package com.kk.pde.ds.rag.internal;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.rag.api.DocumentParser;

/**
 * {@link DocumentParser} that extracts text from the formats the RAG feature
 * needs, choosing a strategy per file type:
 *
 * <ul>
 *   <li><b>.pdf</b> — Apache PDFBox ({@code PDFTextStripper}).</li>
 *   <li><b>.docx / .pptx</b> — read the OOXML zip directly and pull the text out
 *       of the relevant XML parts. OOXML is just a zip of XML, so this needs no
 *       heavyweight office library (and avoids POI's large dependency tree).</li>
 *   <li><b>.html / .htm</b> — strip tags and decode entities.</li>
 *   <li><b>.txt / .md</b> — read as UTF-8.</li>
 *   <li><b>image files</b> (.png/.jpg/.jpeg/.tif/.tiff/.bmp/.gif) — OCR via
 *       {@link OcrEngine}.</li>
 * </ul>
 *
 * <p><b>Text inside images</b> is recovered through OCR (see {@link OcrEngine}):
 * scanned/image-only PDF pages are rasterised and OCR'd, embedded OOXML media
 * parts are OCR'd, and standalone image files are read directly. Scope is limited
 * to <em>reading text out of images</em> — no figure/diagram description. When OCR
 * is unavailable (Tesseract not installed, or {@code -Drag.ocr.enabled=false}) the
 * parser silently falls back to text-layer-only extraction.</p>
 *
 * <p>PDFBox logs via commons-logging (JCL), not slf4j, so it does not perturb the
 * project's slf4j 1.7 / Logback stack. Everything else here is dependency-free.
 * No network access occurs during parsing — the airgap requirement is met by
 * construction (OCR shells out to a local {@code tesseract} binary).</p>
 */
@Component(service = DocumentParser.class)
public class MultiFormatDocumentParser implements DocumentParser {

	private static final Logger LOG = LoggerFactory.getLogger(MultiFormatDocumentParser.class);

	private static final Set<String> TEXT_EXTENSIONS = new HashSet<String>(Arrays.asList(
		"pdf", "docx", "pptx", "txt", "md", "html", "htm"));

	/** Standalone raster image formats handled purely by OCR. */
	private static final Set<String> IMAGE_EXTENSIONS = new HashSet<String>(Arrays.asList(
		"png", "jpg", "jpeg", "tif", "tiff", "bmp", "gif"));

	/** A PDF page whose text layer has fewer than this many non-whitespace
	 *  characters is treated as image-only and sent to OCR. */
	private static final int DEFAULT_PDF_MIN_CHARS_PER_PAGE = 16;

	private OcrEngine ocr;
	private int pdfMinCharsPerPage;

	@Activate
	public void activate() {
		this.ocr = new OcrEngine();
		this.pdfMinCharsPerPage = parseInt(
			System.getProperty("rag.ocr.pdf.min.chars.per.page"), DEFAULT_PDF_MIN_CHARS_PER_PAGE);
		LOG.info("MultiFormatDocumentParser activated (PDFBox + direct OOXML/HTML/text; OCR {})",
			ocr.isEnabled() ? "enabled" : "disabled");
	}

	@Override
	public boolean supports(Path file) {
		String ext = extension(file);
		return TEXT_EXTENSIONS.contains(ext) || IMAGE_EXTENSIONS.contains(ext);
	}

	@Override
	public String extractText(Path file) throws IOException {
		String ext = extension(file);
		if ("pdf".equals(ext)) {
			return extractPdf(file);
		}
		if ("docx".equals(ext)) {
			return extractOoxml(file, "word/document.xml", null);
		}
		if ("pptx".equals(ext)) {
			return extractOoxml(file, null, "ppt/slides/slide");
		}
		if ("html".equals(ext) || "htm".equals(ext)) {
			return htmlToText(readUtf8(file));
		}
		if (IMAGE_EXTENSIONS.contains(ext)) {
			return extractImage(file);
		}
		// txt, md, and anything else supported: plain UTF-8.
		return readUtf8(file);
	}

	// ---- standalone images -------------------------------------------------

	private String extractImage(Path file) throws IOException {
		String text = ocr.ocr(Files.readAllBytes(file));
		if (text.isEmpty() && !ocr.isAvailable()) {
			LOG.warn("Image '{}' yielded no text: OCR is unavailable, so its content "
				+ "cannot be indexed.", file.getFileName());
		}
		return text;
	}

	// ---- PDF ---------------------------------------------------------------

	private String extractPdf(Path file) throws IOException {
		PDDocument doc = PDDocument.load(file.toFile());
		try {
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);

			// Fast path: no OCR available -> single pass over the whole document,
			// identical to the original behaviour.
			if (!ocr.isAvailable()) {
				return stripper.getText(doc);
			}

			// OCR path: walk page by page so we only rasterise pages that lack a
			// real text layer (scanned / image-only pages).
			int pages = doc.getNumberOfPages();
			PDFRenderer renderer = new PDFRenderer(doc);
			StringBuilder out = new StringBuilder();
			int ocrPages = 0;
			for (int i = 0; i < pages; i++) {
				stripper.setStartPage(i + 1);
				stripper.setEndPage(i + 1);
				String pageText = stripper.getText(doc);
				if (nonWhitespaceCount(pageText) >= pdfMinCharsPerPage) {
					out.append(pageText);
					continue;
				}
				// Sparse/empty text layer: treat as an image page and OCR it.
				BufferedImage img = renderer.renderImageWithDPI(i, ocr.dpi());
				String ocrText = ocr.ocr(img);
				if (!ocrText.isEmpty()) {
					out.append(ocrText).append("\n\n");
					ocrPages++;
				} else if (!pageText.isEmpty()) {
					out.append(pageText);
				}
			}
			if (ocrPages > 0) {
				LOG.info("OCR recovered text from {} image-only page(s) in {}", ocrPages, file.getFileName());
			}
			return out.toString();
		} finally {
			doc.close();
		}
	}

	// ---- OOXML (.docx / .pptx) --------------------------------------------

	/**
	 * Extract text from an OOXML package.
	 *
	 * @param exactEntry   a single zip entry to read (docx body), or null
	 * @param entryPrefix  read every entry whose name starts with this prefix
	 *                     and ends in .xml (pptx slides), or null
	 */
	private String extractOoxml(Path file, String exactEntry, String entryPrefix) throws IOException {
		StringBuilder out = new StringBuilder();
		boolean doOcr = ocr.isAvailable();
		List<String> imageTexts = new ArrayList<String>(); // OCR'd media, appended last
		InputStream fis = Files.newInputStream(file);
		try {
			ZipInputStream zip = new ZipInputStream(fis);
			ZipEntry entry;
			// Collect matching slide entries so they can be ordered (slide1, slide2…).
			List<String[]> slideParts = new ArrayList<String[]>(); // [name, xml]
			while ((entry = zip.getNextEntry()) != null) {
				String name = entry.getName();
				if (exactEntry != null && name.equals(exactEntry)) {
					out.append(ooxmlXmlToText(readEntry(zip)));
				} else if (entryPrefix != null && name.startsWith(entryPrefix) && name.endsWith(".xml")) {
					slideParts.add(new String[] { name, readEntry(zip) });
				} else if (doOcr && isOoxmlImage(name)) {
					String t = ocr.ocr(readEntryBytes(zip));
					if (!t.isEmpty()) {
						imageTexts.add(t.trim());
					}
				}
				zip.closeEntry();
			}
			if (!slideParts.isEmpty()) {
				slideParts.sort((a, b) -> compareSlideNames(a[0], b[0]));
				for (String[] part : slideParts) {
					String text = ooxmlXmlToText(part[1]).trim();
					if (!text.isEmpty()) {
						out.append(text).append("\n\n");
					}
				}
			}
			// Append text recovered from embedded images after the body text.
			for (String t : imageTexts) {
				out.append(t).append("\n\n");
			}
			zip.close();
		} finally {
			fis.close();
		}
		return out.toString();
	}

	/** True for OOXML media parts that {@link OcrEngine} can plausibly decode. */
	private static boolean isOoxmlImage(String entryName) {
		String lower = entryName.toLowerCase(Locale.ROOT);
		if (lower.indexOf("/media/") < 0) {
			return false;
		}
		int dot = lower.lastIndexOf('.');
		return dot >= 0 && IMAGE_EXTENSIONS.contains(lower.substring(dot + 1));
	}

	/**
	 * Turn an OOXML part's XML into text: paragraph elements ({@code w:p}/{@code a:p})
	 * become line breaks, every other tag is dropped, and entities are decoded.
	 * The visible text lives in {@code w:t}/{@code a:t} runs, which become bare text
	 * once the surrounding tags are stripped.
	 */
	private String ooxmlXmlToText(String xml) {
		if (xml == null || xml.isEmpty()) {
			return "";
		}
		// Paragraph boundaries -> newlines (handle both Word and PowerPoint namespaces).
		String s = xml.replaceAll("</w:p>", "\n").replaceAll("</a:p>", "\n");
		// Explicit line breaks and tabs.
		s = s.replaceAll("<w:br\\b[^>]*/>", "\n").replaceAll("<w:tab\\b[^>]*/>", "\t");
		// Drop all remaining tags.
		s = s.replaceAll("<[^>]+>", "");
		return collapseBlankLines(decodeXmlEntities(s));
	}

	private static int compareSlideNames(String a, String b) {
		return Integer.compare(slideNumber(a), slideNumber(b));
	}

	private static int slideNumber(String name) {
		// Pull the trailing integer out of ".../slideN.xml".
		int end = name.lastIndexOf(".xml");
		int i = end - 1;
		while (i >= 0 && Character.isDigit(name.charAt(i))) {
			i--;
		}
		try {
			return (i + 1 < end) ? Integer.parseInt(name.substring(i + 1, end)) : 0;
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	// ---- HTML --------------------------------------------------------------

	private String htmlToText(String html) {
		if (html == null) {
			return "";
		}
		// Remove script/style blocks wholesale (case-insensitive, across newlines).
		String s = html.replaceAll("(?is)<script\\b.*?</script>", " ")
			.replaceAll("(?is)<style\\b.*?</style>", " ");
		// Block-ish tags -> newlines so structure survives stripping.
		s = s.replaceAll("(?i)<(br|/p|/div|/li|/h[1-6]|/tr)\\b[^>]*>", "\n");
		// Drop remaining tags + decode entities.
		s = s.replaceAll("<[^>]+>", "");
		return collapseBlankLines(decodeXmlEntities(s));
	}

	// ---- shared helpers ----------------------------------------------------

	private static String readUtf8(Path file) throws IOException {
		return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
	}

	private static String readEntry(ZipInputStream zip) throws IOException {
		return new String(readEntryBytes(zip), StandardCharsets.UTF_8);
	}

	private static byte[] readEntryBytes(ZipInputStream zip) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int n;
		while ((n = zip.read(buf)) != -1) {
			bos.write(buf, 0, n);
		}
		return bos.toByteArray();
	}

	/** Count of non-whitespace characters; used to decide if a PDF page has a real text layer. */
	private static int nonWhitespaceCount(String s) {
		if (s == null) {
			return 0;
		}
		int count = 0;
		for (int i = 0; i < s.length(); i++) {
			if (!Character.isWhitespace(s.charAt(i))) {
				count++;
			}
		}
		return count;
	}

	private static int parseInt(String s, int def) {
		if (s == null || s.isEmpty()) {
			return def;
		}
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	/** Decode the small set of XML/HTML entities that appear in document text. */
	private static String decodeXmlEntities(String s) {
		if (s.indexOf('&') < 0) {
			return s;
		}
		StringBuilder sb = new StringBuilder(s.length());
		int i = 0;
		while (i < s.length()) {
			char c = s.charAt(i);
			if (c == '&') {
				int semi = s.indexOf(';', i + 1);
				if (semi > i && semi - i <= 10) {
					String ent = s.substring(i + 1, semi);
					String rep = entity(ent);
					if (rep != null) {
						sb.append(rep);
						i = semi + 1;
						continue;
					}
				}
			}
			sb.append(c);
			i++;
		}
		return sb.toString();
	}

	private static String entity(String ent) {
		if ("amp".equals(ent)) return "&";
		if ("lt".equals(ent)) return "<";
		if ("gt".equals(ent)) return ">";
		if ("quot".equals(ent)) return "\"";
		if ("apos".equals(ent)) return "'";
		if ("nbsp".equals(ent)) return " ";
		if (ent.length() > 1 && ent.charAt(0) == '#') {
			try {
				int code = (ent.charAt(1) == 'x' || ent.charAt(1) == 'X')
					? Integer.parseInt(ent.substring(2), 16)
					: Integer.parseInt(ent.substring(1));
				return String.valueOf((char) code);
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

	/** Collapse 3+ consecutive newlines to a single blank line; trim trailing spaces. */
	private static String collapseBlankLines(String s) {
		return s.replaceAll("[ \\t]+\n", "\n").replaceAll("\n{3,}", "\n\n").trim();
	}

	private static String extension(Path file) {
		String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		int dot = name.lastIndexOf('.');
		return (dot >= 0 && dot < name.length() - 1) ? name.substring(dot + 1) : "";
	}
}
