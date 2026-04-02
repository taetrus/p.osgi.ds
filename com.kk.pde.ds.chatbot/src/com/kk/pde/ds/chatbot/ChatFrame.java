package com.kk.pde.ds.chatbot;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main chat window. Contains a model selector bar at the top,
 * a scrollable chat display in the center, and an input panel at the bottom.
 */
public class ChatFrame extends JFrame {

	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(ChatFrame.class);

	private final ChatService chatService;
	private final ChatPanel chatPanel;
	private final InputPanel inputPanel;
	private final JComboBox<String> modelCombo;
	private final DefaultComboBoxModel<String> modelComboModel;
	private final JButton refreshModelsButton;
	private boolean suppressModelAction;

	public ChatFrame(ChatService chatService) {
		this.chatService = chatService;

		setTitle("OSGi Chatbot");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setPreferredSize(new Dimension(700, 500));

		JPanel contentPane = new JPanel(new BorderLayout(5, 5));
		contentPane.setBorder(new EmptyBorder(8, 8, 8, 8));
		contentPane.setBackground(DarkTheme.BG_DARK);
		setContentPane(contentPane);

		// --- Top bar: model selector ---
		JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
		topBar.setBackground(DarkTheme.BG_DARK);
		topBar.setOpaque(true);
		JLabel modelLabel = new JLabel("Model:");
		modelLabel.setForeground(DarkTheme.FG_PRIMARY);
		topBar.add(modelLabel);

		modelComboModel = new DefaultComboBoxModel<String>();
		String defaultModel = System.getProperty("openrouter.model", "google/gemini-flash-1.5");
		modelComboModel.addElement(defaultModel);
		modelCombo = new JComboBox<String>(modelComboModel);
		modelCombo.setEditable(true);
		modelCombo.setPreferredSize(new Dimension(350, 26));
		modelCombo.setBackground(DarkTheme.BG_MEDIUM);
		modelCombo.setForeground(DarkTheme.FG_PRIMARY);
		modelCombo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (suppressModelAction) return;
				String selected = (String) modelCombo.getSelectedItem();
				if (selected != null && !selected.isEmpty()) {
					chatService.setModel(selected);
				}
			}
		});
		topBar.add(modelCombo);

		refreshModelsButton = DarkTheme.createButton("Refresh Models");
		refreshModelsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				fetchModelsAsync();
			}
		});
		topBar.add(refreshModelsButton);

		JButton settingsButton = DarkTheme.createButton("Settings");
		settingsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				new SettingsDialog(ChatFrame.this, chatService).setVisible(true);
			}
		});
		topBar.add(settingsButton);

		contentPane.add(topBar, BorderLayout.NORTH);

		// --- Center: chat display ---
		chatPanel = new ChatPanel();
		contentPane.add(chatPanel, BorderLayout.CENTER);

		// --- Bottom: input panel ---
		inputPanel = new InputPanel(
			new InputPanel.SendListener() {
				@Override
				public void onSend(String message) {
					sendMessageAsync(message);
				}
			},
			new InputPanel.ClearListener() {
				@Override
				public void onClear() {
					chatService.clearHistory();
					chatPanel.clear();
					chatPanel.addSystemMessage("Conversation cleared.");
				}
			}
		);
		contentPane.add(inputPanel, BorderLayout.SOUTH);

		pack();
		setLocationRelativeTo(null);
		inputPanel.focusInput();
	}

	private void sendMessageAsync(final String message) {
		chatPanel.addUserMessage(message);
		inputPanel.setSendEnabled(false);
		chatPanel.addSystemMessage("Thinking...");

		new SwingWorker<String, Void>() {
			@Override
			protected String doInBackground() {
				return chatService.send(message);
			}

			@Override
			protected void done() {
				try {
					String response = get();
					chatPanel.addAssistantMessage(response);
				} catch (Exception e) {
					LOG.error("Chat error", e);
					chatPanel.addSystemMessage("Error: " + e.getMessage());
				} finally {
					inputPanel.setSendEnabled(true);
					inputPanel.focusInput();
				}
			}
		}.execute();
	}

	private void fetchModelsAsync() {
		refreshModelsButton.setEnabled(false);
		refreshModelsButton.setText("Loading...");
		chatPanel.addSystemMessage("Fetching tool-capable models from " + chatService.getBaseUrl() + "...");

		new SwingWorker<List<String>, Void>() {
			@Override
			protected List<String> doInBackground() {
				return chatService.fetchModels();
			}

			@Override
			protected void done() {
				try {
					List<String> models = get();
					if (models.isEmpty()) {
						chatPanel.addSystemMessage("No models returned. Check API key and base URL.");
					} else {
						String currentSelection = (String) modelCombo.getSelectedItem();
						suppressModelAction = true;
						try {
							modelComboModel.removeAllElements();
							for (String model : models) {
								modelComboModel.addElement(model);
							}
						} finally {
							suppressModelAction = false;
						}
						if (currentSelection != null) {
							modelCombo.setSelectedItem(currentSelection);
						}
						chatPanel.addSystemMessage("Loaded " + models.size() + " tool-capable models.");
					}
				} catch (Exception e) {
					LOG.error("Failed to fetch models", e);
					chatPanel.addSystemMessage("Error fetching models: " + e.getMessage());
				} finally {
					refreshModelsButton.setEnabled(true);
					refreshModelsButton.setText("Refresh Models");
				}
			}
		}.execute();
	}
}
