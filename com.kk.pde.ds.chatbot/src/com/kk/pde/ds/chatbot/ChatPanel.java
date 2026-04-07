package com.kk.pde.ds.chatbot;

import java.awt.Color;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Panel that displays chat messages with styled formatting.
 * User messages are blue, assistant messages are dark, system/tool messages are gray.
 */
public class ChatPanel extends JScrollPane {

	private static final long serialVersionUID = 1L;
	private final JTextPane textPane;
	private final StyledDocument doc;
	private final Style userStyle;
	private final Style assistantStyle;
	private final Style systemStyle;
	private final Style labelStyle;
	private final Style userLabelStyle;
	private final Style assistantLabelStyle;

	public ChatPanel() {
		textPane = new JTextPane();
		textPane.setEditable(false);
		textPane.setBackground(new Color(30, 30, 40));
		textPane.setFont(new Font("SansSerif", Font.PLAIN, 14));

		// Disable the caret entirely on this read-only pane.
		// Even non-editable JTextPanes have a blinking caret timer (~500ms)
		// that calls repaint(), triggering a StyledDocument layout pass.
		// As the conversation grows this steals increasing EDT time from
		// the input text area's keystroke processing.
		textPane.getCaret().setVisible(false);
		textPane.getCaret().setSelectionVisible(false);
		((javax.swing.text.DefaultCaret) textPane.getCaret())
			.setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);

		doc = textPane.getStyledDocument();

		// User message style (light blue)
		userStyle = doc.addStyle("user", null);
		StyleConstants.setForeground(userStyle, new Color(130, 200, 255));
		StyleConstants.setFontSize(userStyle, 14);

		// Assistant message style (white/light gray)
		assistantStyle = doc.addStyle("assistant", null);
		StyleConstants.setForeground(assistantStyle, new Color(220, 220, 230));
		StyleConstants.setFontSize(assistantStyle, 14);

		// System/tool message style (dimmed)
		systemStyle = doc.addStyle("system", null);
		StyleConstants.setForeground(systemStyle, new Color(120, 120, 140));
		StyleConstants.setFontSize(systemStyle, 12);
		StyleConstants.setItalic(systemStyle, true);

		// Label style (role indicators) — created once, not per message
		labelStyle = doc.addStyle("label", null);
		StyleConstants.setFontSize(labelStyle, 11);
		StyleConstants.setBold(labelStyle, true);

		userLabelStyle = doc.addStyle("userLabel", labelStyle);
		StyleConstants.setForeground(userLabelStyle, new Color(80, 160, 220));

		assistantLabelStyle = doc.addStyle("assistantLabel", labelStyle);
		StyleConstants.setForeground(assistantLabelStyle, new Color(100, 200, 120));

		setViewportView(textPane);
		setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER, 1));
		getVerticalScrollBar().setBackground(DarkTheme.BG_DARK);
	}

	/** Append a user message to the display. */
	public void addUserMessage(final String message) {
		appendStyledMessage("\nYou:\n", userLabelStyle, message + "\n", userStyle);
	}

	/** Append an assistant message with markdown rendering to the display. */
	public void addAssistantMessage(final String message) {
		final Runnable task = new Runnable() {
			@Override
			public void run() {
				try {
					doc.insertString(doc.getLength(), "\nAssistant:\n", assistantLabelStyle);
				} catch (BadLocationException e) {
					// ignore
				}
				MarkdownStyler.appendMarkdown(doc, message, assistantStyle);
				scrollToEnd();
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			task.run();
		} else {
			SwingUtilities.invokeLater(task);
		}
	}

	/** Append a system/status message (tool calls, errors, etc.). */
	public void addSystemMessage(final String message) {
		final Runnable task = new Runnable() {
			@Override
			public void run() {
				try {
					doc.insertString(doc.getLength(), "\n" + message + "\n", systemStyle);
					scrollToEnd();
				} catch (BadLocationException e) {
					// ignore
				}
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			task.run();
		} else {
			SwingUtilities.invokeLater(task);
		}
	}

	/** Clear all displayed messages. */
	public void clear() {
		final Runnable task = new Runnable() {
			@Override
			public void run() {
				try {
					doc.remove(0, doc.getLength());
				} catch (BadLocationException e) {
					// ignore
				}
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			task.run();
		} else {
			SwingUtilities.invokeLater(task);
		}
	}

	/**
	 * Batch a label + message into a single EDT task with one scroll.
	 * This reduces the number of layout recalculations from 3 (label insert +
	 * message insert + scroll) to 1 batched operation.
	 */
	private void appendStyledMessage(final String label, final Style lblStyle,
			final String body, final Style bodyStyle) {
		final Runnable task = new Runnable() {
			@Override
			public void run() {
				try {
					doc.insertString(doc.getLength(), label, lblStyle);
					doc.insertString(doc.getLength(), body, bodyStyle);
					scrollToEnd();
				} catch (BadLocationException e) {
					// ignore
				}
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			task.run();
		} else {
			SwingUtilities.invokeLater(task);
		}
	}

	/** Scroll to end without updating caret position (avoids layout pass). */
	private void scrollToEnd() {
		int len = doc.getLength();
		if (len > 0) {
			try {
				java.awt.Rectangle r = textPane.modelToView(len);
				if (r != null) {
					textPane.scrollRectToVisible(r);
				}
			} catch (BadLocationException e) {
				// ignore
			}
		}
	}
}
