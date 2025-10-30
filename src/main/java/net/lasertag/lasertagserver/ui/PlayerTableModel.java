package net.lasertag.lasertagserver.ui;

import net.lasertag.lasertagserver.core.ActorRegistry;
import net.lasertag.lasertagserver.model.Player;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.Arrays;
import java.util.Set;

import static net.lasertag.lasertagserver.ui.AdminConsole.TEAM_COLORS_NAMES;

public class PlayerTableModel extends AbstractTableModel {
	private final String[] columnNames = {"ID", "Name", "Score", "Health", "MaxBullets", "Damage", "Team", "R-point"};
	private final Set<Integer> editableColumns = Set.of(1, 4, 5, 6);
	private final ActorRegistry actorRegistry;

	public static void initTable(JTable playerTable, ActorRegistry actorRegistry) {
		playerTable.setBorder(BorderFactory.createRaisedSoftBevelBorder());
		playerTable.setRowHeight(playerTable.getRowHeight() + 30);
		playerTable.setRowMargin(20);
		playerTable.setIntercellSpacing(new Dimension(15, 15));
		playerTable.setRowSelectionAllowed(false);

		JComboBox<String> teamColorComboBox = new JComboBox<>();
		for (int i = 0; i < TEAM_COLORS_NAMES.length; i++) {
			teamColorComboBox.addItem(TEAM_COLORS_NAMES[i]);
		}
		playerTable.getColumnModel().getColumn(6).setCellEditor(new DefaultCellEditor(teamColorComboBox));

		playerTable.setDefaultRenderer(Object.class, new TableCellRenderer(actorRegistry, Player.Type.PLAYER));

		Arrays.asList(0, 2, 3, 4, 5, 7).forEach(index -> {
			playerTable.getColumnModel().getColumn(index).setPreferredWidth(30);
		});
		playerTable.getColumnModel().getColumn(1).setPreferredWidth(250);
		playerTable.getColumnModel().getColumn(6).setPreferredWidth(100);
	}


	public PlayerTableModel(ActorRegistry actorRegistry) {
		this.actorRegistry = actorRegistry;
	}

	@Override
	public int getRowCount() {
		return actorRegistry.getPlayers().size();
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
		Player player = actorRegistry.getPlayerById(rowIndex);
		return switch (columnIndex) {
			case 0 -> player.getId();
			case 1 -> player.getName();
			case 2 -> player.getScore();
			case 3 -> player.getHealth();
			case 4 -> player.getBulletsMax();
			case 5 -> player.getDamage();
			case 6 -> TEAM_COLORS_NAMES[player.getTeamId()];
			case 7 -> player.getAssignedRespawnPoint() == -1 ? "" : player.getAssignedRespawnPoint();
			default -> null;
		};
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		Player player = actorRegistry.getPlayers().get(rowIndex);
		switch (columnIndex) {
			case 1 -> player.setName((String) aValue);
			case 4 -> player.setBulletsMax((Integer) aValue);
			case 5 -> player.setDamage((Integer) aValue);
			case 6 -> {
				for (int i = 0; i < TEAM_COLORS_NAMES.length; i++) {
					if (TEAM_COLORS_NAMES[i].equals(aValue)) {
						player.setTeamId(i);
					}
				}
			}
		}
		fireTableCellUpdated(rowIndex, columnIndex);
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return editableColumns.contains(columnIndex);
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		if (columnIndex == 1) {
			return String.class;
		} else {
			return Integer.class;
		}
	}

}
