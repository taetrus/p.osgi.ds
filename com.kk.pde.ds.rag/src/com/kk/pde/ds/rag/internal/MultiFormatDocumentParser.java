package com.kk.pde.ds.rag.internal;

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
 * </ul>
 *
 * <p>PDFBox logs via commons-logging (JCL), not slf4j, so it does not perturb the
 * project's slf4j 1.7 / Logback stack. Everything else here is dependency-free.
 * No network access occurs during parsing — the airgap requirement is met by
 * construction.</p>
 */
@Component(service = DocumentParser.class)
public class MultiFormatDocumentParser implements DocumentParser {

	private static final Logger LOG = LoggerFactory.getLogger(MultiFormatDocumentParser.class);

	private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>(Arrays.asList(
		"pdf", "docx", "pptx", "txt", "md", "html", "htm"));

	@Activate
	public void activate() {
		LOG.info("MultiFormatDocumentParser activated (PDFBox + direct OOXML/HTML/text)");
	}

	@Override
	public boolean supports(Path file) {
		return SUPPORTED_EXTENSIONS.contains(extension(file));
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
		// txt, md, and anything else supported: plain UTF-8.
		return readUtf8(file);
	}

	// ---- PDF ---------------------------------------------------------------

	private String extractPdf(Path file) throws IOException {
		PDDocument doc = PDDocument.load(file.toFile());
		try {
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);
			return stripper.getText(doc);
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
			zip.close();
		} finally {
			fis.close();
		}
		return out.toString();
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
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int n;
		while ((n = zip.read(buf)) != -1) {
			bos.write(buf, 0, n);
		}
		return new String(bos.toByteArray(), StandardCharsets.UTF_8);
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
