package net.lasertag.lasertagserver.core;

import lombok.Getter;
import net.lasertag.lasertagserver.model.MessageToPhone;
import net.lasertag.lasertagserver.model.Player;
import net.lasertag.lasertagserver.ui.AdminConsole;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Component
@Getter
public class Game implements GameEventsListener {
	public static final int STATE_IDLE = 1;
	public static final int STATE_START_PENDING = 2;
	public static final int STATE_PLAYING = 3;

	public static final int TEAM_RED = 1;
	public static final int TEAM_BLUE = 2;
	public static final int TEAM_GREEN = 3;
	public static final int TEAM_YELLOW = 4;
	public static final int TEAM_PURPLE = 5;
	public static final int TEAM_CYAN = 6;

	public static final int GUN_FIRE_INTERVAL_MILLIS = 400;

	private final PlayerRegistry playerRegistry;
	private final PhoneCommunication phoneComm;
	private final GunCommunication gunComm;
	private final VestCommunication vestComm;
	private final AdminConsole adminConsole;
	private final ScheduledExecutorService scheduler =
		Executors.newScheduledThreadPool(4, new DaemonThreadFactory("DaemonScheduler"));

	private volatile int gameState = STATE_IDLE;

	private int fragLimit;
	private boolean teamPlay = false;
	private int timeLimitMinutes;
	private int timeLeftSeconds = 0;
	private int gameStartDelaySeconds = 5;

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
		sendPlayerStateToGunVest(player);
		adminConsole.refreshTable();
	}

	@Override
	public void eventVestGotHit(Player player, Player hitByPlayer) {
		if (!canPlay(player) || !canPlay(hitByPlayer) || !hitByPlayer.canHit()
			|| (teamPlay && player.getTeamId() == hitByPlayer.getTeamId())) {
			return;
		}
		player.setHealth(player.getHealth() - hitByPlayer.getDamage());
		if (player.getHealth() > 0) {
			phoneComm.sendEventToPhone(MessageToPhone.GOT_HIT, player, hitByPlayer.getId());
			phoneComm.sendEventToPhone(MessageToPhone.YOU_HIT_SOMEONE, hitByPlayer, player.getId());
		} else { // killed / scored
			hitByPlayer.setScore(hitByPlayer.getScore() + 1);
			phoneComm.sendEventToPhone(MessageToPhone.YOU_SCORED, hitByPlayer, player.getId());
			var score = teamPlay ? playerRegistry.getTeamScores().get(hitByPlayer.getTeamId()) : hitByPlayer.getScore();
			if (score >= fragLimit) {
				eventConsoleEndGame();
			} else {
				phoneComm.sendEventToPhone(MessageToPhone.YOU_KILLED, player, hitByPlayer.getId());
				sendPlayerStateToGunVest(player);
				playerRegistry.getPlayerById(player.getId()).setRespawnCounter(player.getRespawnTimeSeconds());
				scheduler.schedule(() -> respawnPlayer(player), player.getRespawnTimeSeconds(), java.util.concurrent.TimeUnit.SECONDS);
			}
		}
		phoneComm.sendStatsToAll(gameState == STATE_PLAYING);
		adminConsole.refreshTable();
	}

	@Override
	public void eventConsoleScheduleStartGame() {
		timeLimitMinutes = Integer.parseInt(adminConsole.getIndicatorGameTime().getText());
		fragLimit = Integer.parseInt(adminConsole.getIndicatorFragLimit().getText());
		teamPlay = adminConsole.getGameTypeTeam().isSelected();
		for (Player player : playerRegistry.getPlayers()) {
			player.setScore(0);
			player.reset();
			phoneComm.sendEventToPhone(MessageToPhone.GAME_START, player, teamPlay ? 1 : 0);
		}
		timeLeftSeconds = gameStartDelaySeconds;
		setGameState(STATE_START_PENDING);
		sendStatsToAllPhones();
		adminConsole.refreshTable();
		adminConsole.getIndicatorGameTime().setEditable(false);
		adminConsole.getIndicatorFragLimit().setEditable(false);
		adminConsole.getGameTypeTeam().setEnabled(false);
		scheduler.schedule(this::startGame, gameStartDelaySeconds, java.util.concurrent.TimeUnit.SECONDS);
	}

	private void startGame() {
		setGameState(STATE_PLAYING);
		timeLeftSeconds = timeLimitMinutes * 60;
		for (Player player : playerRegistry.getPlayers()) {
			phoneComm.sendEventToPhone(MessageToPhone.RESPAWN, player, 0);
			sendPlayerStateToGunVest(player);
		}
		sendStatsToAllPhones();
		adminConsole.refreshTable();
	}

	@Override
	public void eventConsoleEndGame() {
		setGameState(STATE_IDLE);
		adminConsole.getIndicatorGameTime().setText(timeLimitMinutes+"");
		adminConsole.getIndicatorGameTime().setEditable(true);
		adminConsole.getIndicatorFragLimit().setText(fragLimit+"");
		adminConsole.getIndicatorFragLimit().setEditable(true);
		adminConsole.getGameTypeTeam().setEnabled(true);

		Player leadPlayer = playerRegistry.getLeadPlayer();
		int leadTeam = playerRegistry.getTeamScores().keySet().iterator().next();
		int winner = teamPlay ? leadTeam : Optional.ofNullable(leadPlayer).map(Player::getId).orElse(0);
		for (Player player : playerRegistry.getPlayers()) {
			phoneComm.sendEventToPhone(MessageToPhone.GAME_OVER, player, winner);
			sendPlayerStateToGunVest(player);
		}
		sendStatsToAllPhones();
	}

	@Override
	public void refreshConsoleTable() {
		adminConsole.refreshTable();
	}

	@Override
	public void onPlayerDataUpdated(Player player) {
		sendStatsToAllPhones();
		sendPlayerStateToGunVest(player);
	}

	private void respawnPlayer(Player player) {
		player.reset();
		sendStatsToAllPhones();
		adminConsole.refreshTable();
		phoneComm.sendEventToPhone(MessageToPhone.RESPAWN, player, 0);
		sendPlayerStateToGunVest(player);
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
				int counter = player.getRespawnCounter();
				if (counter > 0) {
					player.setRespawnCounter(counter - 1);
					phoneComm.sendGameTimeToPlayer(player, counter / 60, counter % 60);
				} else {
					phoneComm.sendGameTimeToPlayer(player, timeLeftSeconds / 60, timeLeftSeconds % 60);
				}
			}
		} else if (gameState == STATE_START_PENDING) {
			adminConsole.getIndicatorGameTime().setText(timeLeftSeconds+"...");
			phoneComm.sendGameTimeToAll(0, timeLeftSeconds);
		}
	}

	@Override
	public void deviceConnected(Player player) {
		sendStatsToAllPhones();
		sendPlayerStateToGunVest(player);
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

	private void sendPlayerStateToGunVest(Player player) {
		boolean gameRunning = gameState == STATE_PLAYING;
		gunComm.sendPlayerStateToClient(player, gameRunning);
		vestComm.sendPlayerStateToClient(player, gameRunning);
	}

}
