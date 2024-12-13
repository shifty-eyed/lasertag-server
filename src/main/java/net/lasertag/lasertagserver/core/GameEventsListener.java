package net.lasertag.lasertagserver.core;

import net.lasertag.lasertagserver.model.Messaging;
import net.lasertag.lasertagserver.model.Player;

public interface GameEventsListener {
	void onMessageFromClient(Messaging.MessageFromClient message);

	void eventConsoleScheduleStartGame();

	void eventConsoleEndGame();

	void refreshConsoleTable();

	void onPlayerDataUpdated(Player player, boolean isNameUpdated);

	void deviceConnected(Player player);
}
