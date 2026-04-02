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

	public ChatPanel() {
		textPane = new JTextPane();
		textPane.setEditable(false);
		textPane.setBackground(new Color(30, 30, 40));
		textPane.setFont(new Font("SansSerif", Font.PLAIN, 14));

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

		// Label style (role indicators)
		labelStyle = doc.addStyle("label", null);
		StyleConstants.setFontSize(labelStyle, 11);
		StyleConstants.setBold(labelStyle, true);

		setViewportView(textPane);
		setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER, 1));
		getVerticalScrollBar().setBackground(DarkTheme.BG_DARK);
	}

	/** Append a user message to the display. */
	public void addUserMessage(String message) {
		Style label = doc.addStyle("userLabel", labelStyle);
		StyleConstants.setForeground(label, new Color(80, 160, 220));
		appendText("\nYou:\n", label);
		appendText(message + "\n", userStyle);
		scrollToBottom();
	}

	/** Append an assistant message to the display. */
	public void addAssistantMessage(String message) {
		Style label = doc.addStyle("assistantLabel", labelStyle);
		StyleConstants.setForeground(label, new Color(100, 200, 120));
		appendText("\nAssistant:\n", label);
		appendText(message + "\n", assistantStyle);
		scrollToBottom();
	}

	/** Append a system/status message (tool calls, errors, etc.). */
	public void addSystemMessage(String message) {
		appendText("\n" + message + "\n", systemStyle);
		scrollToBottom();
	}

	/** Clear all displayed messages. */
	public void clear() {
		try {
			doc.remove(0, doc.getLength());
		} catch (BadLocationException e) {
			// ignore
		}
	}

	private void appendText(String text, Style style) {
		try {
			doc.insertString(doc.getLength(), text, style);
		} catch (BadLocationException e) {
			// ignore
		}
	}

	private void scrollToBottom() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				textPane.setCaretPosition(doc.getLength());
			}
		});
	}
}
