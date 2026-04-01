package com.kk.pde.ds.chatbot;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Input panel with multi-line text area, Send button, and Clear History button.
 * Enter sends the message; Shift+Enter inserts a newline.
 */
public class InputPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	/** Callback interface for when the user submits a message. */
	public interface SendListener {
		void onSend(String message);
	}

	/** Callback interface for when the user clears history. */
	public interface ClearListener {
		void onClear();
	}

	private final JTextArea textArea;
	private final JButton sendButton;
	private final JButton clearButton;

	public InputPanel(final SendListener sendListener, final ClearListener clearListener) {
		setLayout(new BorderLayout(5, 5));

		textArea = new JTextArea(3, 40);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);

		// Enter sends, Shift+Enter adds newline
		textArea.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
					e.consume();
					doSend(sendListener);
				}
			}
		});

		JScrollPane scrollPane = new JScrollPane(textArea);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		add(scrollPane, BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

		clearButton = new JButton("Clear");
		clearButton.setToolTipText("Clear conversation history");
		clearButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (clearListener != null) {
					clearListener.onClear();
				}
			}
		});
		buttonPanel.add(clearButton);

		sendButton = new JButton("Send");
		sendButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				doSend(sendListener);
			}
		});
		buttonPanel.add(sendButton);

		add(buttonPanel, BorderLayout.EAST);
	}

	private void doSend(SendListener listener) {
		String text = textArea.getText().trim();
		if (!text.isEmpty() && listener != null) {
			textArea.setText("");
			listener.onSend(text);
		}
	}

	/** Enable or disable the send button (e.g. while waiting for response). */
	public void setSendEnabled(boolean enabled) {
		sendButton.setEnabled(enabled);
		textArea.setEnabled(enabled);
	}

	/** Request focus on the text area. */
	public void focusInput() {
		textArea.requestFocusInWindow();
	}
}
