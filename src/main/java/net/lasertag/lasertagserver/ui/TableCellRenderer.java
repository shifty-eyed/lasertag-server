package net.lasertag.lasertagserver.ui;

import net.lasertag.lasertagserver.core.ActorRegistry;
import net.lasertag.lasertagserver.model.Actor;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;

import static net.lasertag.lasertagserver.ui.AdminConsole.OFFLINE_COLOR;
import static net.lasertag.lasertagserver.ui.AdminConsole.ONLINE_COLOR;

public class TableCellRenderer extends DefaultTableCellRenderer {

	private final ActorRegistry actorRegistry;
	private final Actor.Type type;

	public TableCellRenderer(ActorRegistry actorRegistry, Actor.Type type) {
		this.actorRegistry = actorRegistry;
		this.type = type;
	}

	@Override
	public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
		java.awt.Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		try {
			Actor actor = actorRegistry.getActorByTypeAndId(type, row);
			c.setForeground(actor.isOnline() ? ONLINE_COLOR : OFFLINE_COLOR);
		} catch (Exception e) {
			c.setForeground(OFFLINE_COLOR);
		}
		return c;
	}
}
