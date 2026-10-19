package com.kk.pde.ds.chatbot;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Settings dialog for configuring the chatbot's API connection.
 * Allows setting API key (masked), model, base URL, and testing the connection.
 */
public class SettingsDialog extends JDialog {

	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(SettingsDialog.class);

	private final ChatService chatService;
	private final JPasswordField apiKeyField;
	private final JTextField modelField;
	private final JTextField baseUrlField;
	private final JLabel statusLabel;
	private final JButton testButton;

	public SettingsDialog(JFrame parent, ChatService chatService) {
		super(parent, "Settings", true);
		this.chatService = chatService;

		setPreferredSize(new Dimension(520, 360));
		setResizable(false);

		JPanel contentPane = new JPanel(new BorderLayout(10, 10));
		contentPane.setBorder(new EmptyBorder(15, 15, 15, 15));
		contentPane.setBackground(DarkTheme.BG_DARK);
		setContentPane(contentPane);

		// --- Form fields ---
		JPanel formPanel = new JPanel(new GridBagLayout());
		formPanel.setBackground(DarkTheme.BG_DARK);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(6, 6, 6, 6);
		gbc.anchor = GridBagConstraints.WEST;

		// API Key
		gbc.gridx = 0; gbc.gridy = 0;
		JLabel apiKeyLabel = new JLabel("API Key:");
		apiKeyLabel.setForeground(DarkTheme.FG_PRIMARY);
		formPanel.add(apiKeyLabel, gbc);

		gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
		apiKeyField = new JPasswordField(30);
		apiKeyField.setBackground(DarkTheme.BG_MEDIUM);
		apiKeyField.setForeground(DarkTheme.FG_PRIMARY);
		apiKeyField.setCaretColor(DarkTheme.FG_PRIMARY);
		apiKeyField.setBorder(DarkTheme.inputBorder());
		// Show only the saved value. A key from -D or the environment stays out of the
		// field, so Save cannot copy it into the plaintext settings file.
		final ChatConfig config = chatService.getConfig();
		apiKeyField.setText(config.getSavedApiKey());
		formPanel.add(apiKeyField, gbc);
		String keySource = config.getFallbackApiKeySource();
		addHint(formPanel, gbc, 1, keySource.isEmpty()
				? "Empty: no key. Set OPENROUTER_API_KEY or enter a key."
				: "Empty: uses the key from " + keySource + ".");

		// Model
		gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
		JLabel modelLabel = new JLabel("Model:");
		modelLabel.setForeground(DarkTheme.FG_PRIMARY);
		formPanel.add(modelLabel, gbc);

		gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
		modelField = new JTextField(30);
		modelField.setBackground(DarkTheme.BG_MEDIUM);
		modelField.setForeground(DarkTheme.FG_PRIMARY);
		modelField.setCaretColor(DarkTheme.FG_PRIMARY);
		modelField.setBorder(DarkTheme.inputBorder());
		modelField.setText(config.getSavedModel());
		formPanel.add(modelField, gbc);
		addHint(formPanel, gbc, 3, "Empty: uses " + config.getFallbackModel() + ".");

		// Base URL
		gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
		JLabel urlLabel = new JLabel("Base URL:");
		urlLabel.setForeground(DarkTheme.FG_PRIMARY);
		formPanel.add(urlLabel, gbc);

		gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
		baseUrlField = new JTextField(30);
		baseUrlField.setBackground(DarkTheme.BG_MEDIUM);
		baseUrlField.setForeground(DarkTheme.FG_PRIMARY);
		baseUrlField.setCaretColor(DarkTheme.FG_PRIMARY);
		baseUrlField.setBorder(DarkTheme.inputBorder());
		baseUrlField.setText(config.getSavedBaseUrl());
		formPanel.add(baseUrlField, gbc);
		addHint(formPanel, gbc, 5, "Empty: uses " + config.getFallbackBaseUrl() + ".");

		contentPane.add(formPanel, BorderLayout.CENTER);

		// --- Bottom: status + buttons ---
		JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
		bottomPanel.setBackground(DarkTheme.BG_DARK);

		statusLabel = new JLabel(" ");
		statusLabel.setForeground(DarkTheme.FG_DIM);
		bottomPanel.add(statusLabel, BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		buttonPanel.setBackground(DarkTheme.BG_DARK);

		testButton = DarkTheme.createButton("Test Connection");
		testButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				testConnection();
			}
		});
		buttonPanel.add(testButton);

		JButton cancelButton = DarkTheme.createButton("Cancel");
		cancelButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				dispose();
			}
		});
		buttonPanel.add(cancelButton);

		JButton saveButton = DarkTheme.createAccentButton("Save");
		saveButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				saveSettings();
			}
		});
		buttonPanel.add(saveButton);

		bottomPanel.add(buttonPanel, BorderLayout.EAST);
		contentPane.add(bottomPanel, BorderLayout.SOUTH);

		pack();
		setLocationRelativeTo(parent);
	}

	/** Add a dim one-line hint in column 1 of the given row. */
	private static void addHint(JPanel formPanel, GridBagConstraints gbc, int row, String text) {
		gbc.gridx = 1; gbc.gridy = row; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
		JLabel hint = new JLabel(text);
		hint.setForeground(DarkTheme.FG_DIM);
		formPanel.add(hint, gbc);
	}

	/**
	 * Write the fields to the settings file exactly as shown. An empty field clears the
	 * saved value, so the setting falls back to -D, the environment or the default.
	 */
	private void saveSettings() {
		chatService.setApiKey(new String(apiKeyField.getPassword()).trim());
		chatService.setModel(modelField.getText().trim());
		chatService.setBaseUrl(baseUrlField.getText().trim());

		chatService.getConfig().saveAsync();
		LOG.info("Settings saved to disk");
		dispose();
	}

	private void testConnection() {
		testButton.setEnabled(false);
		statusLabel.setText("Testing connection...");
		statusLabel.setForeground(DarkTheme.FG_DIM);

		// Test what Save would use: the field value, else the fallback.
		ChatConfig config = chatService.getConfig();
		String urlText = baseUrlField.getText().trim();
		String keyText = new String(apiKeyField.getPassword()).trim();
		final String baseUrl = urlText.isEmpty() ? config.getFallbackBaseUrl() : urlText;
		final String apiKey = keyText.isEmpty() ? config.getFallbackApiKey() : keyText;

		new SwingWorker<List<String>, Void>() {
			@Override
			protected List<String> doInBackground() {
				return ModelFetcher.fetchModels(baseUrl, apiKey);
			}

			@Override
			protected void done() {
				try {
					List<String> models = get();
					if (models.isEmpty()) {
						statusLabel.setText("Connection failed \u2014 no models returned.");
						statusLabel.setForeground(new java.awt.Color(220, 80, 80));
					} else {
						statusLabel.setText("Connected! Found " + models.size() + " tool-capable models.");
						statusLabel.setForeground(DarkTheme.ACCENT_GREEN);
					}
				} catch (Exception e) {
					statusLabel.setText("Error: " + e.getMessage());
					statusLabel.setForeground(new java.awt.Color(220, 80, 80));
				} finally {
					testButton.setEnabled(true);
				}
			}
		}.execute();
	}
}
