package net.lasertag.lasertagserver.core;

import lombok.Getter;
import net.lasertag.lasertagserver.model.MessageToPhone;
import net.lasertag.lasertagserver.model.Player;
import net.lasertag.lasertagserver.ui.AdminConsole;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Component
@Getter
public class Game implements GameEventsListener {

	private final int SHOT_DAMAGE = 10;

	public static final int STATE_IDLE = 1;
	public static final int STATE_START_PENDING = 2;
	public static final int STATE_PLAYING = 3;
	public static final int GUN_FIRE_INTERVAL_MILLIS = 300;

	private final PlayerRegistry playerRegistry;
	private final PhoneCommunication phoneComm;
	private final GunCommunication gunComm;
	private final VestCommunication vestComm;
	private final AdminConsole adminConsole;
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

	private volatile int gameState = STATE_IDLE;

	private int fragLimit = 10;
	private int timeLimitMinutes = 10;
	private int timeLeftSeconds = 0;
	private int gameStartDelaySeconds = 5;
	private final Map<Integer, Integer> playerRespawnCounter;


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

		playerRespawnCounter = new HashMap<>();
		playerRegistry.getPlayers().forEach(player -> playerRespawnCounter.put(player.getId(), 0));
	}

	private boolean canPlay(Player player) {
		return gameState == STATE_PLAYING && player.isAlive();
	}

	@Override
	public void eventGunShot(Player player) {
		if (canPlay(player) && player.getBulletsLeft() > 0) {
			player.shoot();
			phoneComm.sendEventToPhone(MessageToPhone.GUN_SHOT, player, 0);
		} else {
			phoneComm.sendEventToPhone(MessageToPhone.GUN_NO_BULLETS, player, 0);
		}
		adminConsole.refreshTable();
	}

	@Override
	public void eventGunReload(Player player) {
		player.reload();
		phoneComm.sendEventToPhone(MessageToPhone.GUN_RELOAD, player, 0);
		adminConsole.refreshTable();
	}

	@Override
	public void eventVestGotHit(Player player, Player hitByPlayer) {
		if (!canPlay(player) || !canPlay(hitByPlayer) || !hitByPlayer.canHit()) {
			return;
		}
		player.setHealth(player.getHealth() - SHOT_DAMAGE);
		if (player.getHealth() > 0) {
			phoneComm.sendEventToPhone(MessageToPhone.GOT_HIT, player, hitByPlayer.getId());
			phoneComm.sendEventToPhone(MessageToPhone.YOU_HIT_SOMEONE, hitByPlayer, player.getId());
		} else { // killed
			hitByPlayer.setScore(hitByPlayer.getScore() + 1);
			phoneComm.sendEventToPhone(MessageToPhone.YOU_KILLED, player, hitByPlayer.getId());
			phoneComm.sendEventToPhone(MessageToPhone.YOU_SCORED, hitByPlayer, player.getId());
			if (hitByPlayer.getScore() >= fragLimit) {
				eventConsoleEndGame();
			} else {
				playerRespawnCounter.put(player.getId(), player.getRespawnTimeSeconds());
				scheduler.schedule(() -> respawnPlayer(player), player.getRespawnTimeSeconds(), java.util.concurrent.TimeUnit.SECONDS);
				phoneComm.sendStatsToAll(gameState == STATE_PLAYING);
			}
		}
		adminConsole.refreshTable();
	}

	@Override
	public void eventConsoleScheduleStartGame() {
		for (Player player : playerRegistry.getPlayers()) {
			player.setScore(0);
			player.reset();
			phoneComm.sendEventToPhone(MessageToPhone.GAME_START, player, 0);
		}
		timeLeftSeconds = gameStartDelaySeconds;
		setGameState(STATE_START_PENDING);
		sendStatsToAllPhones();
		adminConsole.refreshTable();
		scheduler.schedule(this::startGame, gameStartDelaySeconds, java.util.concurrent.TimeUnit.SECONDS);
	}

	private void startGame() {
		for (Player player : playerRegistry.getPlayers()) {
			phoneComm.sendEventToPhone(MessageToPhone.RESPAWN, player, 0);
		}
		timeLeftSeconds = timeLimitMinutes * 60;
		setGameState(STATE_PLAYING);
		sendStatsToAllPhones();
		adminConsole.refreshTable();
	}

	@Override
	public void eventConsoleEndGame() {
		setGameState(STATE_IDLE);
		Player winner = playerRegistry.getLeadPlayer();
		for (Player player : playerRegistry.getPlayers()) {
			phoneComm.sendEventToPhone(MessageToPhone.GAME_OVER, player, winner == null ? 0 : winner.getId());
		}
		sendStatsToAllPhones();
	}

	@Override
	public void refreshConsoleTable() {
		adminConsole.refreshTable();
	}

	private void respawnPlayer(Player player) {
		player.reset();
		adminConsole.refreshTable();
		phoneComm.sendEventToPhone(MessageToPhone.RESPAWN, player, 0);
	}

	@Scheduled(fixedDelay = 1000, initialDelay = 1000)
	public void updateGameTime() {
		timeLeftSeconds--;
		if (gameState == STATE_PLAYING) {
			if (timeLeftSeconds <= 0) {
				eventConsoleEndGame();
				return;
			}
			adminConsole.getIndicatorGameTime().setText(String.format("%02d:%02d", timeLeftSeconds / 60, timeLeftSeconds % 60));
			for (Player player : playerRegistry.getPlayers()) {
				int counter = playerRespawnCounter.get(player.getId());
				if (counter > 0) {
					playerRespawnCounter.put(player.getId(), counter - 1);
					phoneComm.sendGameTimeToPlayer(player, counter / 60, counter % 60);
				} else {
					phoneComm.sendGameTimeToPlayer(player, timeLeftSeconds / 60, timeLeftSeconds % 60);
				}
			}
		} else if (gameState == STATE_START_PENDING) {
			adminConsole.getIndicatorGameTime().setText(timeLeftSeconds+"...");
			phoneComm.sendGameTimeToAll(0, timeLeftSeconds);
		} else {
			adminConsole.getIndicatorGameTime().setText("--:--");
		}
	}

	@Override
	public void deviceConnected(Player player) {
		sendStatsToAllPhones();
	}

	private void setGameState(int newState) {
		if (gameState != newState) {
			gameState = newState;
			adminConsole.getIndicatorStatus().setText(switch (gameState) {
				case STATE_IDLE -> "Idle";
				case STATE_START_PENDING -> "Starting";
				case STATE_PLAYING -> "Playing";
				default -> "Unknown";
			});
		}
	}

	private void sendStatsToAllPhones() {
		phoneComm.sendStatsToAll(gameState == STATE_PLAYING);
	}

}
