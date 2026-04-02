package com.kk.pde.ds.chatbot;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.UIManager;
import javax.swing.border.Border;

/**
 * Centralized dark theme colors and styling utilities for the chatbot UI.
 */
public final class DarkTheme {

	// Background colors
	public static final Color BG_DARK = new Color(30, 30, 40);
	public static final Color BG_MEDIUM = new Color(45, 45, 58);
	public static final Color BG_LIGHT = new Color(60, 60, 75);

	// Foreground colors
	public static final Color FG_PRIMARY = new Color(220, 220, 230);
	public static final Color FG_DIM = new Color(150, 150, 165);

	// Accent colors
	public static final Color ACCENT_BLUE = new Color(80, 160, 220);
	public static final Color ACCENT_GREEN = new Color(100, 200, 120);

	// Border
	public static final Color BORDER = new Color(70, 70, 85);

	private DarkTheme() {
	}

	/** Apply dark theme defaults to UIManager. Call before creating components. */
	public static void apply() {
		UIManager.put("Panel.background", BG_DARK);
		UIManager.put("Label.foreground", FG_PRIMARY);
		UIManager.put("Button.background", BG_MEDIUM);
		UIManager.put("Button.foreground", FG_PRIMARY);
		UIManager.put("TextField.background", BG_MEDIUM);
		UIManager.put("TextField.foreground", FG_PRIMARY);
		UIManager.put("TextField.caretForeground", FG_PRIMARY);
		UIManager.put("TextArea.background", BG_MEDIUM);
		UIManager.put("TextArea.foreground", FG_PRIMARY);
		UIManager.put("TextArea.caretForeground", FG_PRIMARY);
		UIManager.put("ComboBox.background", BG_MEDIUM);
		UIManager.put("ComboBox.foreground", FG_PRIMARY);
		UIManager.put("ComboBox.selectionBackground", ACCENT_BLUE);
		UIManager.put("ComboBox.selectionForeground", Color.WHITE);
		UIManager.put("List.background", BG_MEDIUM);
		UIManager.put("List.foreground", FG_PRIMARY);
		UIManager.put("List.selectionBackground", ACCENT_BLUE);
		UIManager.put("List.selectionForeground", Color.WHITE);
		UIManager.put("ScrollBar.track", BG_DARK);
		UIManager.put("ScrollBar.thumb", BG_LIGHT);
		UIManager.put("ScrollPane.background", BG_DARK);
		UIManager.put("PasswordField.background", BG_MEDIUM);
		UIManager.put("PasswordField.foreground", FG_PRIMARY);
		UIManager.put("PasswordField.caretForeground", FG_PRIMARY);
		UIManager.put("ToolTip.background", BG_MEDIUM);
		UIManager.put("ToolTip.foreground", FG_PRIMARY);
	}

	/** Create a styled dark button with hover effect. */
	public static JButton createButton(String text) {
		final JButton button = new JButton(text);
		button.setBackground(BG_MEDIUM);
		button.setForeground(FG_PRIMARY);
		button.setFocusPainted(false);
		button.setBorderPainted(true);
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER, 1),
			BorderFactory.createEmptyBorder(4, 12, 4, 12)
		));
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setFont(new Font("SansSerif", Font.PLAIN, 12));

		// Hover effect
		button.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				button.setBackground(BG_LIGHT);
			}
			@Override
			public void mouseExited(MouseEvent e) {
				button.setBackground(BG_MEDIUM);
			}
		});

		return button;
	}

	/** Create a styled accent (primary action) button. */
	public static JButton createAccentButton(String text) {
		final JButton button = createButton(text);
		button.setBackground(ACCENT_BLUE);
		button.setForeground(Color.WHITE);
		button.setFont(new Font("SansSerif", Font.BOLD, 12));

		// Override hover for accent
		button.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				button.setBackground(new Color(100, 180, 240));
			}
			@Override
			public void mouseExited(MouseEvent e) {
				button.setBackground(ACCENT_BLUE);
			}
		});

		return button;
	}

	/** Standard border for input fields. */
	public static Border inputBorder() {
		return BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER, 1),
			BorderFactory.createEmptyBorder(2, 4, 2, 4)
		);
	}
}
