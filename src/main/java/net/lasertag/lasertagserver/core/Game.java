package net.lasertag.lasertagserver.core;

import lombok.Getter;
import net.lasertag.lasertagserver.model.MessageToPhone;
import net.lasertag.lasertagserver.model.Player;
import net.lasertag.lasertagserver.ui.AdminConsole;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Component
@Getter
public class Game implements GameEventsListener {

	private final int SHOT_DAMAGE = 1;

	private final PlayerRegistry playerRegistry;
	private final PhoneCommunication phoneComm;
	private final GunCommunication gunComm;
	private final VestCommunication vestComm;
	private final AdminConsole adminConsole;
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	private boolean gameRunning = false;
	private int fragLimit = 10;
	private int timeLimitMinutes = 10;
	private int timeLeftSeconds = 0;
	private int respawnTimeSeconds = 15;

	public Game(PlayerRegistry playerRegistry, PhoneCommunication phoneComm, GunCommunication gunComm, VestCommunication vestComm, AdminConsole adminConsole) {
		this.playerRegistry = playerRegistry;
		this.phoneComm = phoneComm;
		this.gunComm = gunComm;
		this.vestComm = vestComm;
		this.adminConsole = adminConsole;
		gunComm.setGameEventsListener(this);
		vestComm.setGameEventsListener(this);
		phoneComm.setGameEventsListener(this);
		adminConsole.setGameEventsListener(this);
	}

	private boolean canPlay(Player player) {
		return gameRunning && player.isAlive();
	}

	@Override
	public void eventGunShot(Player player) {
		if (canPlay(player) && player.getBulletsLeft() > 0) {
			player.setBulletsLeft(player.getBulletsLeft() - 1);
			phoneComm.sendEventToPhone(MessageToPhone.GUN_SHOT, player, 0);
		} else {
			phoneComm.sendEventToPhone(MessageToPhone.GUN_NO_BULLETS, player, 0);
		}
		adminConsole.refreshTable();
	}

	@Override
	public void eventGunReload(Player player) {
		player.setBulletsLeft(player.getMagazineSize());
		phoneComm.sendEventToPhone(MessageToPhone.GUN_RELOAD, player, 0);
		adminConsole.refreshTable();
	}

	@Override
	public void eventVestGotHit(Player player, Player hitByPlayer) {
		if (!canPlay(player) || !canPlay(hitByPlayer)) {
			return;
		}
		player.setHealth(player.getHealth() - SHOT_DAMAGE);
		if (player.getHealth() >= 0) {
			phoneComm.sendEventToPhone(MessageToPhone.GOT_HIT, player, hitByPlayer.getId());
			phoneComm.sendEventToPhone(MessageToPhone.YOU_HIT_SOMEONE, hitByPlayer, player.getId());
		} else {
			hitByPlayer.setScore(hitByPlayer.getScore() + 1);
			phoneComm.sendEventToPhone(MessageToPhone.YOU_KILLED, player, hitByPlayer.getId());
			phoneComm.sendEventToPhone(MessageToPhone.YOU_SCORED, hitByPlayer, player.getId());
			if (hitByPlayer.getScore() >= fragLimit) {
				endGame();
			} else {
				scheduler.schedule(() -> respawnPlayer(player), respawnTimeSeconds, java.util.concurrent.TimeUnit.SECONDS);
				phoneComm.sendStatsToAll();
			}
		}
		adminConsole.refreshTable();
	}

	@Override
	public void eventConsoleStartGame() {
		startGame();
	}

	@Override
	public void eventConsoleEndGame() {
		endGame();
	}

	@Override
	public void refreshConsoleTable() {
		adminConsole.refreshTable();
	}

	public void endGame() {
		setGameRunning(false);
		Player winner = playerRegistry.getLeadPlayer();
		for (Player player : playerRegistry.getPlayers()) {
			phoneComm.sendEventToPhone(MessageToPhone.GAME_OVER, player, winner == null ? 0 : winner.getId());
		}
		phoneComm.sendStatsToAll();
	}

	private void respawnPlayer(Player player) {
		player.respawn();
		adminConsole.refreshTable();
		phoneComm.sendEventToPhone(MessageToPhone.RESPAWN, player, 0);
	}

	public void startGame() {
		for (Player player : playerRegistry.getPlayers()) {
			player.setScore(0);
			player.respawn();
			phoneComm.sendEventToPhone(MessageToPhone.GAME_START, player, 0);
		}
		timeLeftSeconds = timeLimitMinutes * 60;
		setGameRunning(true);
		phoneComm.sendStatsToAll();
		adminConsole.refreshTable();
	}

	@Scheduled(fixedRate = 1000)
	public void updateGameTime() {
		if (gameRunning) {
			timeLeftSeconds--;
			if (timeLeftSeconds <= 0) {
				endGame();
			}
			adminConsole.getIndicatorGameTime().setText(String.format("%02d:%02d", timeLeftSeconds / 60, timeLeftSeconds % 60));
		} else {
			adminConsole.getIndicatorGameTime().setText("--:--");
		}
	}

	private void setGameRunning(boolean gameRunning) {
		this.gameRunning = gameRunning;
		adminConsole.getIndicatorStatus().setText(gameRunning ? "Playing" : "Stopped");
	}

}
