package net.lasertag.lasertagserver.ui;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.Arrays;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;
import net.lasertag.lasertagserver.core.Game;
import net.lasertag.lasertagserver.core.GameEventsListener;
import net.lasertag.lasertagserver.core.PlayerRegistry;
import net.lasertag.lasertagserver.model.Player;
import org.springframework.stereotype.Component;

@Component
public class AdminConsole {

	// TODO: Add team score indicator if team play is enabled

	@Getter
	private JTextField indicatorGameTime;
	@Getter
	private JTextField indicatorStatus;
	@Getter
	private JTextField indicatorFragLimit;
	@Getter
	private JCheckBox gameTypeTeam;

	private final PlayerRegistry playerRegistry;
	@Setter
	private GameEventsListener gameEventsListener;

	private PlayerTableModel playerTableModel;
	private JPanel scoresContainer;

	private static final Color[] TEAM_COLORS = {Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.MAGENTA, Color.CYAN};
	private static final String[] TEAM_COLORS_NAMES = {"Red", "Blue", "Green", "Yellow", "Magenta", "Cyan"};

	public AdminConsole(PlayerRegistry playerRegistry) {
		this.playerRegistry = playerRegistry;
		SwingUtilities.invokeLater(this::initUI);
	}

	private void initUI() {
		JFrame frame = new JFrame("Admin Console for Laser Tag Game Server.");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(2000, 700);
		frame.setLayout(new BorderLayout());

		// Table Model and JTable
		playerTableModel = new PlayerTableModel();
		JTable playerTable = new JTable(playerTableModel);
		playerTable.setBorder(BorderFactory.createRaisedSoftBevelBorder());
		playerTable.setRowHeight(playerTable.getRowHeight() + 30);
		playerTable.setRowMargin(20);
		playerTable.setIntercellSpacing(new Dimension(15, 15));
		playerTable.setRowSelectionAllowed(false);
		JComboBox<String> teamColorComboBox = new JComboBox<>();
		for (int i = 0; i < TEAM_COLORS_NAMES.length; i++) {
			teamColorComboBox.addItem(TEAM_COLORS_NAMES[i]);
		}
		playerTable.getColumnModel().getColumn(9).setCellEditor(new DefaultCellEditor(teamColorComboBox));


		JScrollPane tableScrollPane = new JScrollPane(playerTable);
		frame.add(tableScrollPane, BorderLayout.CENTER);

		JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 20));

		// Bottom Panel with Buttons and Timer
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

		frame.add(topPanel, BorderLayout.NORTH);
		frame.add(bottomPanel, BorderLayout.SOUTH);

		frame.setVisible(true);
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
		playerRegistry.getTeamScores().forEach((teamId, score) -> {
			JLabel label = new JLabel(" "+score+" ");
			label.setFont(new Font("Arial", Font.BOLD, 35));
			//label.setOpaque(true);
			if (teamId < Game.TEAM_RED || teamId > Game.TEAM_CYAN) {
				return;
			}
			var color = TEAM_COLORS[(teamId - Game.TEAM_RED)];
			label.setForeground(color);
			scoresContainer.add(label);
		});

		scoresContainer.revalidate();
		scoresContainer.repaint();

	}

	public void refreshTable() {
		SwingUtilities.invokeLater(() -> {
			playerTableModel.fireTableDataChanged();
			refreshTeamScores();
		});
	}

	private class PlayerTableModel extends AbstractTableModel {
		private final String[] columnNames = {"ID", "Name", "Score", "Health", "MaxHealth", "BulletsLeft", "Magazine", "RespawnTime", "Damage", "Team", "Online"};
		private final Set<Integer> editableColumns = Set.of(1, 4, 6, 7, 8, 9);
		@Override
		public int getRowCount() {
			return playerRegistry.getPlayers().size();
		}

		@Override
		public int getColumnCount() {
			return columnNames.length;
		}

		@Override
		public String getColumnName(int column) {
			return columnNames[column];
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			Player player = playerRegistry.getPlayers().get(rowIndex);
			return switch (columnIndex) {
				case 0 -> player.getId();
				case 1 -> player.getName();
				case 2 -> player.getScore();
				case 3 -> player.getHealth();
				case 4 -> player.getMaxHealth();
				case 5 -> player.getBulletsLeft();
				case 6 -> player.getMagazineSize();
				case 7 -> player.getRespawnTimeSeconds();
				case 8 -> player.getDamage();
				case 9 -> TEAM_COLORS_NAMES[player.getTeamId() - Game.TEAM_RED];
				case 10 -> player.devicesOnline();
				default -> null;
			};
		}

		@Override
		public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			Player player = playerRegistry.getPlayers().get(rowIndex);
			switch (columnIndex) {
				case 1 -> player.setName((String) aValue);
				case 4 -> player.setMaxHealth((Integer) aValue);
				case 6 -> player.setMagazineSize((Integer) aValue);
				case 7 -> player.setRespawnTimeSeconds((Integer) aValue);
				case 8 -> player.setDamage((Integer) aValue);
				case 9 -> {
					for (int i = 0; i < TEAM_COLORS_NAMES.length; i++) {
						if (TEAM_COLORS_NAMES[i].equals(aValue)) {
							player.setTeamId(i + Game.TEAM_RED);
						}
					}
				}
			};
			gameEventsListener.onPlayerDataUpdated(player);
			fireTableCellUpdated(rowIndex, columnIndex);
			SwingUtilities.invokeLater(AdminConsole.this::refreshTeamScores);
		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			return editableColumns.contains(columnIndex);
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			if (columnIndex == 1 || columnIndex == 9) {
				return String.class;
			} else {
				return Integer.class;
			}
		}

	}


}

