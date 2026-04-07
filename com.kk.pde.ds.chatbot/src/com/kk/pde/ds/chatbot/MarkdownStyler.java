package com.kk.pde.ds.chatbot;

import java.awt.Font;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;

/**
 * Converts markdown text into styled segments for a {@link StyledDocument}.
 * Uses commonmark-java to parse the AST, then walks it with a visitor that
 * collects {@link StyledSegment}s. This two-phase design allows the parsing
 * to run off the EDT while only the insertion phase needs the EDT.
 *
 * <p>Supports GFM tables via the commonmark-ext-gfm-tables extension.
 */
public final class MarkdownStyler {

	private static final Parser PARSER = Parser.builder()
		.extensions(Arrays.asList(TablesExtension.create()))
		.build();

	private MarkdownStyler() {
	}

	/**
	 * A text segment paired with its styling attributes.
	 * Thread-safe value object — can be created on any thread and
	 * inserted into a StyledDocument on the EDT.
	 */
	static final class StyledSegment {
		final String text;
		final AttributeSet attrs;

		StyledSegment(String text, AttributeSet attrs) {
			this.text = text;
			this.attrs = attrs;
		}
	}

	/**
	 * Parse markdown and collect styled segments. This method is thread-safe
	 * and does not require the EDT — it performs no Swing component access.
	 *
	 * @param markdown  raw markdown text from the LLM
	 * @param baseAttrs the base attributes for assistant text (color, font size)
	 * @return immutable list of styled segments ready for insertion
	 */
	public static List<StyledSegment> parseMarkdown(String markdown, AttributeSet baseAttrs) {
		Node document = PARSER.parse(markdown);
		SegmentCollector collector = new SegmentCollector(baseAttrs);
		document.accept(collector);
		return Collections.unmodifiableList(collector.segments);
	}

	/**
	 * Parse markdown and insert styled text directly into the document.
	 * Convenience method that combines parsing and insertion — must be
	 * called on the EDT since it modifies the StyledDocument.
	 */
	public static void appendMarkdown(StyledDocument doc, String markdown, Style baseStyle) {
		List<StyledSegment> segments = parseMarkdown(markdown, baseStyle);
		insertSegments(doc, segments);
	}

	/**
	 * Insert pre-parsed segments into a StyledDocument. Must be called on the EDT.
	 */
	public static void insertSegments(StyledDocument doc, List<StyledSegment> segments) {
		try {
			for (StyledSegment seg : segments) {
				doc.insertString(doc.getLength(), seg.text, seg.attrs);
			}
		} catch (BadLocationException e) {
			// should not happen when appending at end
		}
	}

	/**
	 * AST visitor that collects styled text segments without touching any
	 * Swing component. Uses {@link SimpleAttributeSet} (a standalone
	 * AttributeSet implementation) instead of document-bound Styles,
	 * so the visitor can run on any thread.
	 *
	 * <p>Common attribute sets (bold, italic, code, headings, etc.) are
	 * pre-created in the constructor and reused across all visits.
	 */
	private static class SegmentCollector extends AbstractVisitor {

		final List<StyledSegment> segments = new ArrayList<StyledSegment>();

		private final AttributeSet baseAttrs;
		private final Deque<AttributeSet> styleStack = new ArrayDeque<AttributeSet>();

		// Pre-created reusable attribute sets
		private final AttributeSet boldAttrs;
		private final AttributeSet italicAttrs;
		private final AttributeSet codeAttrs;
		private final AttributeSet codeBlockAttrs;
		private final AttributeSet quoteAttrs;
		private final AttributeSet linkAttrs;
		private final AttributeSet imageAttrs;
		private final AttributeSet heading1Attrs;
		private final AttributeSet heading2Attrs;
		private final AttributeSet heading3Attrs;
		private final AttributeSet tableAttrs;
		private final AttributeSet tableHeaderAttrs;
		private final AttributeSet tableSepAttrs;

		private int listDepth = 0;
		private int orderedIndex = 0;
		private boolean inOrderedList = false;

		SegmentCollector(AttributeSet baseAttrs) {
			this.baseAttrs = baseAttrs;
			styleStack.push(baseAttrs);

			// Pre-create attribute sets for all formatting types
			boldAttrs = derive(baseAttrs, new Applicator() {
				public void apply(SimpleAttributeSet s) {
					StyleConstants.setBold(s, true);
				}
			});
			italicAttrs = derive(baseAttrs, new Applicator() {
				public void apply(SimpleAttributeSet s) {
					StyleConstants.setItalic(s, true);
				}
			});
			codeAttrs = derive(baseAttrs, new Applicator() {
				public void apply(SimpleAttributeSet s) {
					StyleConstants.setFontFamily(s, Font.MONOSPACED);
					StyleConstants.setForeground(s, DarkTheme.MD_CODE_FG);
				}
			});
			codeBlockAttrs = derive(baseAttrs, new Applicator() {
				public void apply(SimpleAttributeSet s) {
					StyleConstants.setFontFamily(s, Font.MONOSPACED);
					StyleConstants.setForeground(s, DarkTheme.MD_CODE_BLOCK_FG);
					StyleConstants.setFontSize(s, 13);
				}
			});
			quoteAttrs = derive(baseAttrs, new Applicator() {
				public void apply(SimpleAttributeSet s) {
					StyleConstants.setItalic(s, true);
					StyleConstants.setForeground(s, DarkTheme.MD_QUOTE_FG);
				}
			});
			linkAttrs = derive(baseAttrs, new Applicator() {
				public void apply(SimpleAttributeSet s) {
					StyleConstants.setForeground(s, DarkTheme.ACCENT_BLUE);
					StyleConstants.setUnderline(s, true);
				}
			});
			imageAttrs = derive(baseAttrs, new Applicator() {
				public void apply(SimpleAttributeSet s) {
					StyleConstants.setForeground(s, DarkTheme.FG_DIM);
				}
			});
			heading1Attrs = deriveHeading(baseAttrs, 20);
			heading2Attrs = deriveHeading(baseAttrs, 17);
			heading3Attrs = deriveHeading(baseAttrs, 15);
			tableAttrs = derive(baseAttrs, new Applicator() {
				public void apply(SimpleAttributeSet s) {
					StyleConstants.setFontFamily(s, Font.MONOSPACED);
					StyleConstants.setFontSize(s, 13);
				}
			});
			tableHeaderAttrs = derive(tableAttrs, new Applicator() {
				public void apply(SimpleAttributeSet s) {
					StyleConstants.setBold(s, true);
					StyleConstants.setForeground(s, DarkTheme.MD_HEADING_FG);
				}
			});
			tableSepAttrs = derive(tableAttrs, new Applicator() {
				public void apply(SimpleAttributeSet s) {
					StyleConstants.setForeground(s, DarkTheme.FG_DIM);
				}
			});
		}

		// ── Text nodes ─────────────────────────────────────

		@Override
		public void visit(Text text) {
			collect(text.getLiteral(), styleStack.peek());
		}

		@Override
		public void visit(SoftLineBreak softLineBreak) {
			collect(" ", styleStack.peek());
		}

		@Override
		public void visit(HardLineBreak hardLineBreak) {
			collect("\n", styleStack.peek());
		}

		// ── Block-level nodes ──────────────────────────────

		@Override
		public void visit(Paragraph paragraph) {
			visitChildren(paragraph);
			if (!(paragraph.getParent() instanceof ListItem)) {
				collect("\n\n", styleStack.peek());
			} else {
				collect("\n", styleStack.peek());
			}
		}

		@Override
		public void visit(Heading heading) {
			int level = heading.getLevel();
			AttributeSet headingAttrs;
			if (level == 1) {
				headingAttrs = heading1Attrs;
			} else if (level == 2) {
				headingAttrs = heading2Attrs;
			} else {
				headingAttrs = heading3Attrs;
			}
			styleStack.push(headingAttrs);
			visitChildren(heading);
			styleStack.pop();
			collect("\n\n", styleStack.peek());
		}

		@Override
		public void visit(BlockQuote blockQuote) {
			styleStack.push(quoteAttrs);
			collect("  > ", quoteAttrs);
			visitChildren(blockQuote);
			styleStack.pop();
		}

		@Override
		public void visit(FencedCodeBlock fencedCodeBlock) {
			collectCodeBlock(fencedCodeBlock.getLiteral());
		}

		@Override
		public void visit(IndentedCodeBlock indentedCodeBlock) {
			collectCodeBlock(indentedCodeBlock.getLiteral());
		}

		@Override
		public void visit(ThematicBreak thematicBreak) {
			collect("\n────────────────────────────────────\n\n", styleStack.peek());
		}

		// ── Lists ──────────────────────────────────────────

		@Override
		public void visit(BulletList bulletList) {
			listDepth++;
			boolean wasOrdered = inOrderedList;
			inOrderedList = false;
			visitChildren(bulletList);
			inOrderedList = wasOrdered;
			listDepth--;
			if (listDepth == 0) {
				collect("\n", styleStack.peek());
			}
		}

		@Override
		public void visit(OrderedList orderedList) {
			listDepth++;
			boolean wasOrdered = inOrderedList;
			int prevIndex = orderedIndex;
			inOrderedList = true;
			orderedIndex = orderedList.getStartNumber();
			visitChildren(orderedList);
			inOrderedList = wasOrdered;
			orderedIndex = prevIndex;
			listDepth--;
			if (listDepth == 0) {
				collect("\n", styleStack.peek());
			}
		}

		@Override
		public void visit(ListItem listItem) {
			StringBuilder indent = new StringBuilder();
			for (int i = 1; i < listDepth; i++) {
				indent.append("    ");
			}
			if (inOrderedList) {
				collect(indent.toString() + "  " + orderedIndex + ". ", styleStack.peek());
				orderedIndex++;
			} else {
				collect(indent.toString() + "  - ", styleStack.peek());
			}
			visitChildren(listItem);
		}

		// ── Inline formatting ──────────────────────────────

		@Override
		public void visit(StrongEmphasis strongEmphasis) {
			// For nested styles, derive from current stack top
			AttributeSet current = styleStack.peek();
			AttributeSet derived;
			if (current == baseAttrs) {
				derived = boldAttrs; // reuse pre-created
			} else {
				derived = derive(current, new Applicator() {
					public void apply(SimpleAttributeSet s) {
						StyleConstants.setBold(s, true);
					}
				});
			}
			styleStack.push(derived);
			visitChildren(strongEmphasis);
			styleStack.pop();
		}

		@Override
		public void visit(Emphasis emphasis) {
			AttributeSet current = styleStack.peek();
			AttributeSet derived;
			if (current == baseAttrs) {
				derived = italicAttrs;
			} else {
				derived = derive(current, new Applicator() {
					public void apply(SimpleAttributeSet s) {
						StyleConstants.setItalic(s, true);
					}
				});
			}
			styleStack.push(derived);
			visitChildren(emphasis);
			styleStack.pop();
		}

		@Override
		public void visit(Code code) {
			collect(code.getLiteral(), codeAttrs);
		}

		@Override
		public void visit(Link link) {
			styleStack.push(linkAttrs);
			visitChildren(link);
			styleStack.pop();
		}

		@Override
		public void visit(Image image) {
			collect("[" + image.getTitle() + "]", imageAttrs);
		}

		// ── HTML blocks (pass through as plain text) ──────

		@Override
		public void visit(HtmlBlock htmlBlock) {
			collect(htmlBlock.getLiteral(), styleStack.peek());
		}

		@Override
		public void visit(HtmlInline htmlInline) {
			collect(htmlInline.getLiteral(), styleStack.peek());
		}

		// ── Tables (GFM extension) ────────────────────────

		@Override
		public void visit(CustomBlock customBlock) {
			if (customBlock instanceof TableBlock) {
				collectTable((TableBlock) customBlock);
			} else {
				visitChildren(customBlock);
			}
		}

		@Override
		public void visit(CustomNode customNode) {
			visitChildren(customNode);
		}

		private void collectTable(TableBlock table) {
			// Pass 1: collect all rows (header + body) as string arrays
			List<String[]> rows = new ArrayList<String[]>();
			int headerRowCount = 0;

			Node section = table.getFirstChild();
			while (section != null) {
				if (section instanceof TableHead) {
					Node row = section.getFirstChild();
					while (row != null) {
						if (row instanceof TableRow) {
							rows.add(collectRowCells((TableRow) row));
							headerRowCount++;
						}
						row = row.getNext();
					}
				} else if (section instanceof TableBody) {
					Node row = section.getFirstChild();
					while (row != null) {
						if (row instanceof TableRow) {
							rows.add(collectRowCells((TableRow) row));
						}
						row = row.getNext();
					}
				}
				section = section.getNext();
			}

			if (rows.isEmpty()) return;

			// Compute max column widths
			int colCount = 0;
			for (String[] row : rows) {
				if (row.length > colCount) colCount = row.length;
			}
			int[] widths = new int[colCount];
			for (String[] row : rows) {
				for (int c = 0; c < row.length; c++) {
					if (row[c].length() > widths[c]) {
						widths[c] = row[c].length();
					}
				}
			}

			// Pass 2: render with padding
			collect("\n", baseAttrs);

			for (int r = 0; r < rows.size(); r++) {
				String[] row = rows.get(r);
				boolean isHeader = r < headerRowCount;
				AttributeSet cellAttrs = isHeader ? tableHeaderAttrs : tableAttrs;

				for (int c = 0; c < colCount; c++) {
					if (c > 0) {
						collect(" | ", tableSepAttrs);
					}
					String cell = c < row.length ? row[c] : "";
					collect(pad(cell, widths[c]), cellAttrs);
				}
				collect("\n", baseAttrs);

				// Draw separator line after header
				if (isHeader && r == headerRowCount - 1) {
					for (int c = 0; c < colCount; c++) {
						if (c > 0) {
							collect("-+-", tableSepAttrs);
						}
						collect(repeat('-', widths[c]), tableSepAttrs);
					}
					collect("\n", baseAttrs);
				}
			}
			collect("\n", baseAttrs);
		}

		private String[] collectRowCells(TableRow row) {
			List<String> cells = new ArrayList<String>();
			Node cell = row.getFirstChild();
			while (cell != null) {
				if (cell instanceof TableCell) {
					cells.add(extractPlainText(cell));
				}
				cell = cell.getNext();
			}
			return cells.toArray(new String[0]);
		}

		private String extractPlainText(Node node) {
			StringBuilder sb = new StringBuilder();
			Node child = node.getFirstChild();
			while (child != null) {
				if (child instanceof Text) {
					sb.append(((Text) child).getLiteral());
				} else if (child instanceof Code) {
					sb.append(((Code) child).getLiteral());
				} else if (child instanceof SoftLineBreak) {
					sb.append(' ');
				} else {
					sb.append(extractPlainText(child));
				}
				child = child.getNext();
			}
			return sb.toString();
		}

		private String pad(String text, int width) {
			if (text.length() >= width) return text;
			StringBuilder sb = new StringBuilder(text);
			for (int i = text.length(); i < width; i++) {
				sb.append(' ');
			}
			return sb.toString();
		}

		private String repeat(char c, int count) {
			char[] chars = new char[count];
			Arrays.fill(chars, c);
			return new String(chars);
		}

		// ── Helpers ────────────────────────────────────────

		private void collectCodeBlock(String code) {
			collect("\n", baseAttrs);
			String trimmed = code;
			if (trimmed.endsWith("\n")) {
				trimmed = trimmed.substring(0, trimmed.length() - 1);
			}
			collect(trimmed, codeBlockAttrs);
			collect("\n\n", baseAttrs);
		}

		private void collect(String text, AttributeSet attrs) {
			segments.add(new StyledSegment(text, attrs));
		}

		// ── Attribute set factories ────────────────────────

		/** Callback for applying style attributes. */
		private interface Applicator {
			void apply(SimpleAttributeSet s);
		}

		private static SimpleAttributeSet derive(AttributeSet parent, Applicator applicator) {
			SimpleAttributeSet s = new SimpleAttributeSet(parent);
			applicator.apply(s);
			return s;
		}

		private static SimpleAttributeSet deriveHeading(AttributeSet parent, int fontSize) {
			SimpleAttributeSet s = new SimpleAttributeSet(parent);
			StyleConstants.setBold(s, true);
			StyleConstants.setForeground(s, DarkTheme.MD_HEADING_FG);
			StyleConstants.setFontSize(s, fontSize);
			return s;
		}
	}
}
