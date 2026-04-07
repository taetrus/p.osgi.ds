package com.kk.pde.ds.chatbot;

import java.awt.Font;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import javax.swing.text.BadLocationException;
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
 * Converts markdown text into styled segments inserted into a {@link StyledDocument}.
 * Uses commonmark-java to parse the AST, then walks it with a visitor that maintains
 * a style stack for nested formatting (e.g. bold inside italic).
 * Supports GFM tables via the commonmark-ext-gfm-tables extension.
 */
public final class MarkdownStyler {

	private static final Parser PARSER = Parser.builder()
		.extensions(Arrays.asList(TablesExtension.create()))
		.build();

	private MarkdownStyler() {
	}

	/**
	 * Parse the given markdown and append styled text to the document.
	 *
	 * @param doc       the styled document to append to
	 * @param markdown  raw markdown text from the LLM
	 * @param baseStyle the base style for assistant text (color, font size)
	 */
	public static void appendMarkdown(StyledDocument doc, String markdown, Style baseStyle) {
		Node document = PARSER.parse(markdown);
		document.accept(new StyledDocVisitor(doc, baseStyle));
	}

	/**
	 * AST visitor that inserts styled text into a StyledDocument.
	 * Maintains a Deque of styles so nested formatting composes correctly.
	 */
	private static class StyledDocVisitor extends AbstractVisitor {

		private final StyledDocument doc;
		private final Style baseStyle;
		private final Deque<Style> styleStack = new ArrayDeque<Style>();
		private int listDepth = 0;
		private int orderedIndex = 0;
		private boolean inOrderedList = false;
		StyledDocVisitor(StyledDocument doc, Style baseStyle) {
			this.doc = doc;
			this.baseStyle = baseStyle;
			styleStack.push(baseStyle);
		}

		// ── Text nodes ─────────────────────────────────────

		@Override
		public void visit(Text text) {
			insert(text.getLiteral(), styleStack.peek());
		}

		@Override
		public void visit(SoftLineBreak softLineBreak) {
			insert(" ", styleStack.peek());
		}

		@Override
		public void visit(HardLineBreak hardLineBreak) {
			insert("\n", styleStack.peek());
		}

		// ── Block-level nodes ──────────────────────────────

		@Override
		public void visit(Paragraph paragraph) {
			visitChildren(paragraph);
			if (!(paragraph.getParent() instanceof ListItem)) {
				insert("\n\n", styleStack.peek());
			} else {
				insert("\n", styleStack.peek());
			}
		}

		@Override
		public void visit(Heading heading) {
			Style headingStyle = doc.addStyle(null, styleStack.peek());
			StyleConstants.setBold(headingStyle, true);
			StyleConstants.setForeground(headingStyle, DarkTheme.MD_HEADING_FG);
			int level = heading.getLevel();
			if (level == 1) {
				StyleConstants.setFontSize(headingStyle, 20);
			} else if (level == 2) {
				StyleConstants.setFontSize(headingStyle, 17);
			} else {
				StyleConstants.setFontSize(headingStyle, 15);
			}
			styleStack.push(headingStyle);
			visitChildren(heading);
			styleStack.pop();
			insert("\n\n", styleStack.peek());
		}

		@Override
		public void visit(BlockQuote blockQuote) {
			Style quoteStyle = doc.addStyle(null, styleStack.peek());
			StyleConstants.setItalic(quoteStyle, true);
			StyleConstants.setForeground(quoteStyle, DarkTheme.MD_QUOTE_FG);
			styleStack.push(quoteStyle);
			insert("  > ", quoteStyle);
			visitChildren(blockQuote);
			styleStack.pop();
		}

		@Override
		public void visit(FencedCodeBlock fencedCodeBlock) {
			insertCodeBlock(fencedCodeBlock.getLiteral());
		}

		@Override
		public void visit(IndentedCodeBlock indentedCodeBlock) {
			insertCodeBlock(indentedCodeBlock.getLiteral());
		}

		@Override
		public void visit(ThematicBreak thematicBreak) {
			insert("\n────────────────────────────────────\n\n", styleStack.peek());
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
				insert("\n", styleStack.peek());
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
				insert("\n", styleStack.peek());
			}
		}

		@Override
		public void visit(ListItem listItem) {
			StringBuilder indent = new StringBuilder();
			for (int i = 1; i < listDepth; i++) {
				indent.append("    ");
			}
			if (inOrderedList) {
				insert(indent.toString() + "  " + orderedIndex + ". ", styleStack.peek());
				orderedIndex++;
			} else {
				insert(indent.toString() + "  - ", styleStack.peek());
			}
			visitChildren(listItem);
		}

		// ── Inline formatting ──────────────────────────────

		@Override
		public void visit(StrongEmphasis strongEmphasis) {
			Style boldStyle = doc.addStyle(null, styleStack.peek());
			StyleConstants.setBold(boldStyle, true);
			styleStack.push(boldStyle);
			visitChildren(strongEmphasis);
			styleStack.pop();
		}

		@Override
		public void visit(Emphasis emphasis) {
			Style italicStyle = doc.addStyle(null, styleStack.peek());
			StyleConstants.setItalic(italicStyle, true);
			styleStack.push(italicStyle);
			visitChildren(emphasis);
			styleStack.pop();
		}

		@Override
		public void visit(Code code) {
			Style codeStyle = doc.addStyle(null, styleStack.peek());
			StyleConstants.setFontFamily(codeStyle, Font.MONOSPACED);
			StyleConstants.setForeground(codeStyle, DarkTheme.MD_CODE_FG);
			insert(code.getLiteral(), codeStyle);
		}

		@Override
		public void visit(Link link) {
			Style linkStyle = doc.addStyle(null, styleStack.peek());
			StyleConstants.setForeground(linkStyle, DarkTheme.ACCENT_BLUE);
			StyleConstants.setUnderline(linkStyle, true);
			styleStack.push(linkStyle);
			visitChildren(link);
			styleStack.pop();
		}

		@Override
		public void visit(Image image) {
			Style imgStyle = doc.addStyle(null, styleStack.peek());
			StyleConstants.setForeground(imgStyle, DarkTheme.FG_DIM);
			insert("[" + image.getTitle() + "]", imgStyle);
		}

		// ── HTML blocks (pass through as plain text) ──────

		@Override
		public void visit(HtmlBlock htmlBlock) {
			insert(htmlBlock.getLiteral(), styleStack.peek());
		}

		@Override
		public void visit(HtmlInline htmlInline) {
			insert(htmlInline.getLiteral(), styleStack.peek());
		}

		// ── Tables (GFM extension) ────────────────────────
		// Two-pass rendering: first collect all cell text and compute
		// column widths, then render with monospaced padding so columns
		// align properly.

		@Override
		public void visit(CustomBlock customBlock) {
			if (customBlock instanceof TableBlock) {
				renderTable((TableBlock) customBlock);
			} else {
				visitChildren(customBlock);
			}
		}

		@Override
		public void visit(CustomNode customNode) {
			// Table nodes are handled by renderTable(); this handles
			// any other custom extensions that might be present.
			visitChildren(customNode);
		}

		private void renderTable(TableBlock table) {
			// Pass 1: collect all rows (header + body) as string arrays
			List<String[]> rows = new ArrayList<String[]>();
			int headerRowCount = 0;

			// Collect header rows
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
			Style tableStyle = doc.addStyle(null, baseStyle);
			StyleConstants.setFontFamily(tableStyle, Font.MONOSPACED);
			StyleConstants.setFontSize(tableStyle, 13);

			Style headerStyle = doc.addStyle(null, tableStyle);
			StyleConstants.setBold(headerStyle, true);
			StyleConstants.setForeground(headerStyle, DarkTheme.MD_HEADING_FG);

			Style sepStyle = doc.addStyle(null, tableStyle);
			StyleConstants.setForeground(sepStyle, DarkTheme.FG_DIM);

			insert("\n", baseStyle);

			for (int r = 0; r < rows.size(); r++) {
				String[] row = rows.get(r);
				boolean isHeader = r < headerRowCount;
				Style cellStyle = isHeader ? headerStyle : tableStyle;

				for (int c = 0; c < colCount; c++) {
					if (c > 0) {
						insert(" | ", sepStyle);
					}
					String cell = c < row.length ? row[c] : "";
					insert(pad(cell, widths[c]), cellStyle);
				}
				insert("\n", baseStyle);

				// Draw separator line after header
				if (isHeader && r == headerRowCount - 1) {
					for (int c = 0; c < colCount; c++) {
						if (c > 0) {
							insert("-+-", sepStyle);
						}
						insert(repeat('-', widths[c]), sepStyle);
					}
					insert("\n", baseStyle);
				}
			}
			insert("\n", baseStyle);
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
					// Recurse into inline formatting (bold, italic, etc.)
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

		private void insertCodeBlock(String code) {
			Style codeBlockStyle = doc.addStyle(null, baseStyle);
			StyleConstants.setFontFamily(codeBlockStyle, Font.MONOSPACED);
			StyleConstants.setForeground(codeBlockStyle, DarkTheme.MD_CODE_BLOCK_FG);
			StyleConstants.setFontSize(codeBlockStyle, 13);

			insert("\n", baseStyle);
			String trimmed = code;
			if (trimmed.endsWith("\n")) {
				trimmed = trimmed.substring(0, trimmed.length() - 1);
			}
			insert(trimmed, codeBlockStyle);
			insert("\n\n", baseStyle);
		}

		private void insert(String text, Style style) {
			try {
				doc.insertString(doc.getLength(), text, style);
			} catch (BadLocationException e) {
				// should not happen when appending at end
			}
		}
	}
}
