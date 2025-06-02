package net.lasertag.lasertagserver.ui;

import javax.swing.*;
import java.awt.*;

import lombok.Getter;
import lombok.Setter;
import net.lasertag.lasertagserver.core.GameEventsListener;
import net.lasertag.lasertagserver.core.ActorRegistry;
import net.lasertag.lasertagserver.model.Actor;
import net.lasertag.lasertagserver.model.Messaging;
import net.lasertag.lasertagserver.model.Player;
import net.lasertag.lasertagserver.model.RespawnPoint;
import org.springframework.stereotype.Component;

@Component
public class AdminConsole {

	@Getter
	private JTextField indicatorGameTime;
	@Getter
	private JTextField indicatorStatus;
	@Getter
	private JTextField indicatorFragLimit;
	@Getter
	private JCheckBox gameTypeTeam;

	private final ActorRegistry actorRegistry;
	@Setter
	private GameEventsListener gameEventsListener;

	private PlayerTableModel playerTableModel;
	private DispenserTableModel healthDispenserModel;
	private DispenserTableModel ammoDispenserModel;
	private JPanel scoresContainer;
	private JPanel respawnPointsContainer;

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
		healthDispenserModel = initDispenserTable(Actor.Type.HEALTH_DISPENSER, "Health Dispensers", dispensersContainer);
		ammoDispenserModel = initDispenserTable(Actor.Type.AMMO_DISPENSER, "Ammo Dispensers", dispensersContainer);

		JPanel mainPanel = new JPanel(new GridLayout(2, 1));
		mainPanel.add(tableScrollPane, BorderLayout.CENTER);
		mainPanel.add(dispensersContainer, BorderLayout.SOUTH);

		frame.add(mainPanel, BorderLayout.CENTER);

		JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 20));
		JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 20));

		indicatorStatus = addIndicator("Status:", 10, topPanel);
		indicatorStatus.setEditable(false);

		indicatorGameTime = addIndicator("Game Time:", 5, topPanel);
		indicatorGameTime.setText("15");

		indicatorFragLimit = addIndicator("Frag Limit:", 3, topPanel);
		indicatorFragLimit.setText("10");

		bottomPanel.add(makeButton("Start Game", () -> gameEventsListener.eventConsoleScheduleStartGame()));
		bottomPanel.add(makeButton("End Game", () -> gameEventsListener.eventConsoleEndGame()));

		gameTypeTeam = new JCheckBox("Team Play");
		bottomPanel.add(gameTypeTeam);
		gameTypeTeam.addActionListener(e -> refreshTeamScores());

		scoresContainer = new JPanel();
		bottomPanel.add(scoresContainer);

		// Create respawn points container with horizontal layout
		JPanel respawnPointsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
		respawnPointsPanel.setBorder(BorderFactory.createTitledBorder("Respawn Points"));
		respawnPointsContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
		respawnPointsPanel.add(respawnPointsContainer);
		topPanel.add(respawnPointsPanel);
		refreshRespawnPoints();

		frame.add(topPanel, BorderLayout.NORTH);
		frame.add(bottomPanel, BorderLayout.SOUTH);

		frame.setVisible(true);
	}

	private DispenserTableModel initDispenserTable(Actor.Type type, String title, JPanel container) {
		DispenserTableModel model = new DispenserTableModel(actorRegistry, type);
		model.addTableModelListener(event -> {
			gameEventsListener.onDispenserSettingsUpdated();
		});

		JTable table = new JTable(model);
		table.setDefaultRenderer(Object.class, new TableCellRenderer(actorRegistry, type));
		table.setRowHeight(table.getRowHeight() + 30);
		table.setRowMargin(20);
		table.setIntercellSpacing(new Dimension(15, 15));
		table.setRowSelectionAllowed(false);
		//table.setFont(new Font("Monospaced", Font.PLAIN, 30));

		JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.setBorder(BorderFactory.createTitledBorder(title));
		container.add(scrollPane);
		return model;
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

	private void refreshRespawnPoints() {
		respawnPointsContainer.removeAll();

		actorRegistry.streamByType(Actor.Type.RESPAWN_POINT).forEach(actor -> {
			RespawnPoint respawnPoint = (RespawnPoint) actor;
			JLabel label = new JLabel(" " + respawnPoint.getId() + " ");
			label.setFont(new Font("Arial", Font.BOLD, 30));
			label.setOpaque(true);
			label.setBackground(respawnPoint.isOnline() ? ONLINE_COLOR : OFFLINE_COLOR);
			label.setForeground(respawnPoint.isOnline() ?Color.BLACK : Color.GRAY);
			respawnPointsContainer.add(label);
		});

		respawnPointsContainer.revalidate();
		respawnPointsContainer.repaint();
	}

	public void refreshTable() {
		SwingUtilities.invokeLater(() -> {
			playerTableModel.fireTableDataChanged();
			refreshTeamScores();
			refreshRespawnPoints();
			healthDispenserModel.fireTableDataChanged();
			ammoDispenserModel.fireTableDataChanged();
		});
	}


}
