package net.lasertag.lasertagserver.ui;

import net.lasertag.lasertagserver.core.ActorRegistry;
import net.lasertag.lasertagserver.model.Actor;
import net.lasertag.lasertagserver.model.Dispenser;
import net.lasertag.lasertagserver.model.Player;

import javax.swing.table.AbstractTableModel;
import java.util.Set;

import static net.lasertag.lasertagserver.ui.AdminConsole.TEAM_COLORS_NAMES;

public class DispenserTableModel extends AbstractTableModel {
	private final String[] columnNames = {"ID", "Amount", "Timeout"};
	private final Set<Integer> editableColumns = Set.of(1, 2);

	private final ActorRegistry actorRegistry;
	private final Actor.Type type;

	public DispenserTableModel(ActorRegistry actorRegistry, Actor.Type type) {
		this.actorRegistry = actorRegistry;
		this.type = type;
	}

	@Override
	public int getRowCount() {
		return actorRegistry.getActorCountByType(type);
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
		Dispenser dispenser = (Dispenser)actorRegistry.getActorByTypeAndId(type, rowIndex);
		return switch (columnIndex) {
			case 0 -> dispenser.getId();
			case 1 -> dispenser.getAmount();
			case 2 -> dispenser.getDispenseTimeoutSec();
			default -> null;
		};
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		Dispenser dispenser = (Dispenser)actorRegistry.getActorByTypeAndId(type, rowIndex);
		switch (columnIndex) {
			case 1 -> dispenser.setAmount((Integer)aValue);
			case 2 -> dispenser.setDispenseTimeoutSec((Integer)aValue);
		}
		fireTableCellUpdated(rowIndex, columnIndex);
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return editableColumns.contains(columnIndex);
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		return Integer.class;
	}

}
