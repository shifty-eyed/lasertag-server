package net.lasertag.lasertagserver.ui;

import javax.swing.*;
import java.awt.*;
import java.util.stream.Collectors;

import lombok.Setter;
import net.lasertag.lasertagserver.core.GameEventsListener;
import net.lasertag.lasertagserver.core.ActorRegistry;
import net.lasertag.lasertagserver.model.Actor;
import net.lasertag.lasertagserver.model.Dispenser;
import net.lasertag.lasertagserver.model.Messaging;
import net.lasertag.lasertagserver.model.Player;
import org.springframework.stereotype.Component;

@Component
public class AdminConsole {

	private JTextField filedTimeLimit;
	private JTextField indicatorStatus;
	private JTextField filedFragLimit;
	private JCheckBox gameTypeTeam;

	private final ActorRegistry actorRegistry;
	@Setter
	private GameEventsListener gameEventsListener;

	private PlayerTableModel playerTableModel;

	private JPanel scoresContainer;
	private JPanel mainPanel;

	private static final Color[] TEAM_COLORS = {Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.MAGENTA, Color.CYAN};
	private static final Color[] TEAM_COLORS_TEXT = {Color.WHITE, Color.WHITE, Color.BLACK, Color.BLACK, Color.WHITE, Color.BLACK};
	public static final String[] TEAM_COLORS_NAMES = {"Red", "Blue", "Green", "Yellow", "Magenta", "Cyan"};

	public static final Color ONLINE_COLOR = new Color(200, 255, 255);
	public static final Color OFFLINE_COLOR = new Color(200, 150, 100);

	public AdminConsole(ActorRegistry actorRegistry) {
		this.actorRegistry = actorRegistry;
		SwingUtilities.invokeLater(this::initUI);
	}

	private void initUI() {
		JFrame frame = new JFrame("Laser Tag Game Server.");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(2000, 1000);
		frame.setLayout(new BorderLayout());

		// Table Model and JTable
		playerTableModel = new PlayerTableModel(actorRegistry);
		playerTableModel.addTableModelListener(event -> {
			int rowIndex = event.getFirstRow();
			int columnIndex = event.getColumn();
			Player player = actorRegistry.getPlayers().get(rowIndex);
			gameEventsListener.onPlayerDataUpdated(player, columnIndex == 1);
			SwingUtilities.invokeLater(AdminConsole.this::refreshTeamScores);
		});
		JTable playerTable = new JTable(playerTableModel);
		PlayerTableModel.initTable(playerTable, actorRegistry);

		JScrollPane tableScrollPane = new JScrollPane(playerTable);

		JPanel dispensersContainer = new JPanel(new GridLayout(1, 2));
		initDispenserPanel(Actor.Type.HEALTH_DISPENSER, "Health Dispensers", dispensersContainer);
		initDispenserPanel(Actor.Type.AMMO_DISPENSER, "Ammo Dispensers", dispensersContainer);

		mainPanel = new JPanel(new BorderLayout());
		mainPanel.add(tableScrollPane, BorderLayout.CENTER);
		mainPanel.add(dispensersContainer, BorderLayout.SOUTH);

		frame.add(mainPanel, BorderLayout.CENTER);

		JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 20));
		JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 20));
		
		filedTimeLimit = addIndicator("Time Limit:", 5, topPanel);
		filedTimeLimit.setText("15");
		
		filedFragLimit = addIndicator("Frag Limit:", 3, topPanel);
		filedFragLimit.setText("10");
		
		gameTypeTeam = new JCheckBox("Team Play");
		topPanel.add(gameTypeTeam);
		gameTypeTeam.addActionListener(e -> refreshTeamScores());
		
		bottomPanel.add(makeButton("Start Game", this::onStartGameButtonClicked));
		bottomPanel.add(makeButton("End Game", () -> gameEventsListener.eventConsoleEndGame()));
		
		indicatorStatus = addIndicator("Status:", 30, bottomPanel);
		indicatorStatus.setEditable(false);

		scoresContainer = new JPanel();
		topPanel.add(scoresContainer);

		frame.add(topPanel, BorderLayout.NORTH);
		frame.add(bottomPanel, BorderLayout.SOUTH);

		frame.setVisible(true);
	}

	public void updateGameTimeStatus(int timeLeftSeconds) {
		indicatorStatus.setText("Playing, time left: " + String.format("%02d:%02d", timeLeftSeconds / 60, timeLeftSeconds % 60));
	}

	public void refreshUI(boolean isPlaying) {
		indicatorStatus.setText(isPlaying ? "Playing" : "Idle");
		filedTimeLimit.setEnabled(isPlaying ? false : true);
		filedFragLimit.setEnabled(isPlaying ? false : true);
		gameTypeTeam.setEnabled(isPlaying ? false : true);
		// refresh table and team scores
		SwingUtilities.invokeLater(() -> {
			mainPanel.revalidate();
			mainPanel.repaint();
			refreshTeamScores();
		});
	}

	private void onStartGameButtonClicked() {
		var timeLimitMinutes = Integer.parseInt(filedTimeLimit.getText());
		var fragLimit = Integer.parseInt(filedFragLimit.getText());
		var teamPlay = gameTypeTeam.isSelected() && actorRegistry.getTeamScores().size() > 1;
		gameEventsListener.eventConsoleStartGame(timeLimitMinutes, fragLimit, teamPlay);
	}


	private void initDispenserPanel(Actor.Type type, String title, JPanel container) {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createEtchedBorder());
		
		JLabel onlineLabel = new JLabel(title);
		updateOnlineDispensersLabel(title, onlineLabel, type);
		panel.add(onlineLabel, BorderLayout.NORTH);
		
		JPanel fieldsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
		
		JLabel timeoutLabel = new JLabel("Timeout:");
		JTextField timeoutField = new JTextField(8);
		JLabel amountLabel = new JLabel("Amount:");
		JTextField amountField = new JTextField(8);
		
		fieldsPanel.add(timeoutLabel);
		fieldsPanel.add(timeoutField);
		fieldsPanel.add(amountLabel);
		fieldsPanel.add(amountField);
		
		timeoutField.addActionListener(e -> {
			try {
				int timeout = Integer.parseInt(timeoutField.getText());
				updateAllDispensers(type, timeout, -1);
				
			} catch (NumberFormatException ex) {}
		});
		
		amountField.addActionListener(e -> {
			try {
				int amount = Integer.parseInt(amountField.getText());
				updateAllDispensers(type, -1, amount);
			} catch (NumberFormatException ex) {}
		});
		
		panel.add(fieldsPanel, BorderLayout.SOUTH);
		container.add(panel);
	}
	
	private void updateOnlineDispensersLabel(String title, JLabel label, Actor.Type type) {
		String onlineIds = actorRegistry.streamByType(type)
			.filter(Actor::isOnline)
			.map(actor -> String.valueOf(actor.getId()))
			.collect(Collectors.joining(", "));
		
		if (onlineIds.isEmpty()) {
			label.setText(title + ": none online");
		} else {
			label.setText(title + ": " + onlineIds);
		}
	}
	
	private void updateAllDispensers(Actor.Type type, int timeout, int amount) {
		actorRegistry.streamByType(type)
		.forEach(actor -> {
			Dispenser dispenser = (Dispenser) actor;
			if (timeout > 0) {
				dispenser.setDispenseTimeoutSec(timeout);
			}
			if (amount > 0) {
				dispenser.setAmount(amount);
			}
		});
		gameEventsListener.onDispenserSettingsUpdated();
	}

	private JButton makeButton(String text, Runnable action) {
		JButton button = new JButton(text);
		button.addActionListener(e -> action.run());
		button.setMargin(new Insets(10, 20, 10, 20));
		return button;
	}

	private JTextField addIndicator(String labelText, int columns, JPanel container) {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JLabel label = new JLabel(labelText);
		JTextField textField = new JTextField(columns);
		textField.setHighlighter(null);
		panel.add(label);
		panel.add(textField);
		container.add(panel);
		return textField;
	}

	private void refreshTeamScores() {
		scoresContainer.removeAll();
		if (!gameTypeTeam.isSelected()) {
			return;
		}
		actorRegistry.getTeamScores().forEach((teamId, score) -> {
			JLabel label = new JLabel(" " + score + " ");
			label.setFont(new Font("Monospaced", Font.BOLD, 30));
			var colorId = teamId - Messaging.TEAM_RED;
			label.setBackground(TEAM_COLORS[colorId]);
			label.setForeground(TEAM_COLORS_TEXT[colorId]);
			label.setOpaque(true);
			scoresContainer.add(label);
		});

		scoresContainer.revalidate();
		scoresContainer.repaint();
	}


}
