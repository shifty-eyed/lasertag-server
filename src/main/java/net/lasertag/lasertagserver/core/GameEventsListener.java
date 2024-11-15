package net.lasertag.lasertagserver.core;

import net.lasertag.lasertagserver.model.Player;

public interface GameEventsListener {
	void eventGunShot(Player player);

	void eventGunReload(Player player);

	void eventVestGotHit(Player player, Player hitByPlayer);

	void eventConsoleScheduleStartGame();

	void eventConsoleEndGame();

	void refreshConsoleTable();

	void onPlayerDataUpdated(Player player);

	void deviceConnected(Player player);
}
