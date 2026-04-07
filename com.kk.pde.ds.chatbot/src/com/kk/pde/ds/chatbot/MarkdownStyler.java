package com.kk.pde.ds.chatbot;

import java.awt.Font;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.commonmark.ext.gfm.tables.*;
import org.commonmark.node.*;
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
		private boolean firstTableRow = false;

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

		@Override
		public void visit(CustomBlock customBlock) {
			if (customBlock instanceof TableBlock) {
				insert("\n", styleStack.peek());
				firstTableRow = true;
				visitChildren(customBlock);
				insert("\n", styleStack.peek());
			} else {
				visitChildren(customBlock);
			}
		}

		@Override
		public void visit(CustomNode customNode) {
			if (customNode instanceof TableHead) {
				Style headerStyle = doc.addStyle(null, styleStack.peek());
				StyleConstants.setBold(headerStyle, true);
				StyleConstants.setForeground(headerStyle, DarkTheme.MD_HEADING_FG);
				styleStack.push(headerStyle);
				visitChildren(customNode);
				styleStack.pop();
			} else if (customNode instanceof TableBody) {
				visitChildren(customNode);
			} else if (customNode instanceof TableRow) {
				if (!firstTableRow) {
					insert("\n", styleStack.peek());
				}
				firstTableRow = false;
				visitChildren(customNode);
			} else if (customNode instanceof TableCell) {
				TableCell cell = (TableCell) customNode;
				if (!isFirstCell(cell)) {
					Style sepStyle = doc.addStyle(null, styleStack.peek());
					StyleConstants.setForeground(sepStyle, DarkTheme.FG_DIM);
					insert("  |  ", sepStyle);
				}
				visitChildren(customNode);
			} else {
				visitChildren(customNode);
			}
		}

		private boolean isFirstCell(TableCell cell) {
			return cell.getPrevious() == null;
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
