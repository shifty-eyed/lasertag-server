package net.lasertag.lasertagserver.ui;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;

import lombok.Getter;
import lombok.Setter;
import net.lasertag.lasertagserver.core.GameEventsListener;
import net.lasertag.lasertagserver.core.PlayerRegistry;
import net.lasertag.lasertagserver.model.Player;
import org.springframework.stereotype.Component;

@Component
public class AdminConsole {

	@Getter
	private JTextField indicatorGameTime;
	@Getter
	private JTextField indicatorStatus;

	private final PlayerRegistry playerRegistry;
	@Setter
	private GameEventsListener gameEventsListener;

	private PlayerTableModel playerTableModel;

	public AdminConsole(PlayerRegistry playerRegistry) {
		this.playerRegistry = playerRegistry;
		SwingUtilities.invokeLater(this::initUI);
	}

	private void initUI() {
		JFrame frame = new JFrame("Admin Console for Laser Tag Game Server");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(1600, 500);
		frame.setLayout(new BorderLayout());

		// Table Model and JTable
		playerTableModel = new PlayerTableModel();
		JTable playerTable = new JTable(playerTableModel);
		playerTable.setBorder(BorderFactory.createRaisedSoftBevelBorder());
		playerTable.setRowHeight(playerTable.getRowHeight() + 30);
		playerTable.setRowMargin(20);
		playerTable.setIntercellSpacing(new Dimension(15, 15));
		playerTable.setRowSelectionAllowed(false);

		JScrollPane tableScrollPane = new JScrollPane(playerTable);
		frame.add(tableScrollPane, BorderLayout.CENTER);

		// Bottom Panel with Buttons and Timer
		JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 20));

		indicatorStatus = addIndicator("Status:", 10, bottomPanel);
		indicatorGameTime = addIndicator("Game Time:", 5, bottomPanel);

		bottomPanel.add(makeButton("Start Game", () -> gameEventsListener.eventConsoleStartGame()));
		bottomPanel.add(makeButton("End Game", () -> gameEventsListener.eventConsoleEndGame()));

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
		textField.setEditable(false);
		panel.add(label);
		panel.add(textField);
		container.add(panel);
		return textField;
	}

	public void refreshTable() {
		SwingUtilities.invokeLater(() -> playerTableModel.fireTableDataChanged());
	}

	private class PlayerTableModel extends AbstractTableModel {
		private final String[] columnNames = {"ID", "Name", "Score", "Health", "Max Health", "Bullets Left", "Magazine", "Online"};

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
				case 7 -> player.devicesOnline();
				default -> null;
			};
		}

		@Override
		public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			Player player = playerRegistry.getPlayers().get(rowIndex);
			switch (columnIndex) {
				case 1 -> player.setName((String) aValue);
				case 4 -> player.setMaxHealth((Integer) aValue);
			};
			fireTableCellUpdated(rowIndex, columnIndex);
		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			return columnIndex == 1 || columnIndex == 4;
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			if (columnIndex == 0 || columnIndex == 2 || columnIndex == 3 || columnIndex == 4 || columnIndex == 5 || columnIndex == 6) {
				return Integer.class;
			} else {
				return String.class;
			}
		}

	}


}

