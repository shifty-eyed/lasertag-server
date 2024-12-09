package net.lasertag.lasertagserver.core;

import lombok.Getter;
import net.lasertag.lasertagserver.model.Messaging;
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

	public static final int MAX_HEALTH = 100;
	public static final int MAGAZINE_SIZE = 10;
	public static final int RESPAWN_TIME_SECONDS = 20;

	private final PlayerRegistry playerRegistry;
	private final UdpServer phoneComm;
	private final AdminConsole adminConsole;
	private final ScheduledExecutorService scheduler =
		Executors.newScheduledThreadPool(2, new DaemonThreadFactory("DaemonScheduler"));

	private volatile int gameState = STATE_IDLE;

	private int fragLimit;
	private boolean teamPlay = false;
	private int timeLimitMinutes;
	private int timeLeftSeconds = 0;

	public Game(PlayerRegistry playerRegistry, UdpServer phoneComm, AdminConsole adminConsole) {
		this.playerRegistry = playerRegistry;
		this.phoneComm = phoneComm;
		this.adminConsole = adminConsole;
		phoneComm.setGameEventsListener(this);
		adminConsole.setGameEventsListener(this);
	}

	@Override
	public void onMessageFromClient(Messaging.MessageFromClient message) {
		var player = playerRegistry.getPlayerById(message.getPlayerId());
		player.setHealth(message.getHealth());
		player.setScore(message.getScore());
		player.setBulletsLeft(message.getBulletsLeft());

		if (message.getType() == Messaging.GOT_HIT || message.getType() == Messaging.YOU_KILLED) {
			var hitByPlayer = playerRegistry.getPlayerById(message.getOtherPlayerId());
			if (message.getType() == Messaging.YOU_KILLED) {
				hitByPlayer.setScore(hitByPlayer.getScore() + 1);//may not need because of the score update from the phone
				phoneComm.sendEventToPhone(Messaging.YOU_SCORED, hitByPlayer, player.getId());
				var score = teamPlay ? playerRegistry.getTeamScores().get(hitByPlayer.getTeamId()) : hitByPlayer.getScore();
				if (score >= fragLimit) {
					eventConsoleEndGame();
				} else {
					//todo what to do with respawn timer, maybe do it purely on the phone side, and have 2 time counters, for game and respawn
					// Messaging.RESPAWN should be sent by the phone to server
					//scheduler.schedule(() -> respawnPlayer(player), player.getRespawnTimeSeconds(), java.util.concurrent.TimeUnit.SECONDS);
				}
			} else {
				phoneComm.sendEventToPhone(Messaging.YOU_HIT_SOMEONE, hitByPlayer, player.getId());
			}
		}
		sendStatsToAllPhones();
		adminConsole.refreshTable();
	}

	@Override
	public void eventConsoleScheduleStartGame() {
		timeLimitMinutes = Integer.parseInt(adminConsole.getIndicatorGameTime().getText());
		fragLimit = Integer.parseInt(adminConsole.getIndicatorFragLimit().getText());
		teamPlay = adminConsole.getGameTypeTeam().isSelected();
		timeLeftSeconds = timeLimitMinutes * 60 + RESPAWN_TIME_SECONDS;
		var startGameMessage = Messaging.eventStartGameToBytes(teamPlay, RESPAWN_TIME_SECONDS, timeLimitMinutes);
		for (Player player : playerRegistry.getPlayers()) {
			player.setScore(0);
			player.setHealth(MAX_HEALTH);
			player.setBulletsLeft(MAGAZINE_SIZE);
			phoneComm.sendBytesToClient(player, startGameMessage);
		}
		setGameState(STATE_START_PENDING);
		sendStatsToAllPhones();
		adminConsole.refreshTable();
		adminConsole.getIndicatorGameTime().setEditable(false);
		adminConsole.getIndicatorFragLimit().setEditable(false);
		adminConsole.getGameTypeTeam().setEnabled(false);
		scheduler.schedule(this::startGame, RESPAWN_TIME_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
	}

	private void startGame() {
		setGameState(STATE_PLAYING);
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
		int leadTeam = playerRegistry.getLeadTeam();
		int winner = teamPlay ? leadTeam : Optional.ofNullable(leadPlayer).map(Player::getId).orElse(-1);
		for (Player player : playerRegistry.getPlayers()) {
			phoneComm.sendEventToPhone(Messaging.GAME_OVER, player, winner);
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
		} else if (gameState == STATE_START_PENDING) {
			adminConsole.getIndicatorGameTime().setText(timeLeftSeconds+"...");
		}
	}

	@Override
	public void deviceConnected(Player player) {
		sendStatsToAllPhones();
		if (gameState == STATE_PLAYING) {
			phoneComm.sendTimeCorrectionToPlayer(player, timeLeftSeconds / 60, timeLeftSeconds % 60);
		}
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
		phoneComm.sendStatsToAll(gameState == STATE_PLAYING, teamPlay);
	}

}
