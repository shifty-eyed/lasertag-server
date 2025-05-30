package net.lasertag.lasertagserver.core;

import lombok.Getter;
import net.lasertag.lasertagserver.model.Actor;
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
	public static final int STATE_PLAYING = 2;

	public static final int MAX_HEALTH = 100;

	private final ActorRegistry actorRegistry;
	private final UdpServer udpServer;
	private final AdminConsole adminConsole;
	private final ScheduledExecutorService scheduler =
		Executors.newScheduledThreadPool(2, new DaemonThreadFactory("DaemonScheduler"));

	private volatile int gameState = STATE_IDLE;

	private int fragLimit;
	private boolean teamPlay = false;
	private int timeLimitMinutes;
	private int timeLeftSeconds = 0;

	public Game(ActorRegistry actorRegistry, UdpServer udpServer, AdminConsole adminConsole) {
		this.actorRegistry = actorRegistry;
		this.udpServer = udpServer;
		this.adminConsole = adminConsole;
		udpServer.setGameEventsListener(this);
		adminConsole.setGameEventsListener(this);
	}

	@Override
	public void onMessageFromPlayer(Player player, Messaging.MessageFromClient message) {
		player.setHealth(message.getHealth());
		switch (message.getType()) {
			case Messaging.GOT_HIT | Messaging.YOU_KILLED -> {
				var hitByPlayer = actorRegistry.getPlayerById(message.getExtraValue());
				if (message.getType() == Messaging.YOU_KILLED) {
					hitByPlayer.setScore(hitByPlayer.getScore() + 1);
					udpServer.sendEventToClient(Messaging.YOU_SCORED, hitByPlayer, player.getId());
					player.setAssignedRespawnPoint(actorRegistry.getRandomRespawnPointId());
					var vitalScore = teamPlay ? actorRegistry.getTeamScores().get(hitByPlayer.getTeamId()) : hitByPlayer.getScore();
					if (vitalScore >= fragLimit) {
						eventConsoleEndGame();
					}
				} else {
					udpServer.sendEventToClient(Messaging.YOU_HIT_SOMEONE, hitByPlayer, player.getId());
				}
				sendPlayerValuesSnapshotToAll(false);
			}
			case Messaging.GOT_HEALTH | Messaging.GOT_AMMO -> {
				var type = message.getType() == Messaging.GOT_HEALTH ? Actor.Type.HEALTH_DISPENSER : Actor.Type.AMMO_DISPENSER;
				Actor actor = actorRegistry.getActorByTypeAndId(type, message.getExtraValue());
				udpServer.sendEventToClient(Messaging.DISPENSER_USED, actor, 0);
			}
		}
		adminConsole.refreshTable();
	}

	@Override
	public void eventConsoleScheduleStartGame() {
		timeLimitMinutes = Integer.parseInt(adminConsole.getIndicatorGameTime().getText());
		fragLimit = Integer.parseInt(adminConsole.getIndicatorFragLimit().getText());
		teamPlay = adminConsole.getGameTypeTeam().isSelected() && actorRegistry.getTeamScores().size() > 1;
		timeLeftSeconds = timeLimitMinutes * 60;

		var respawnPointsIt = actorRegistry.shuffledRespawnPointIds().iterator();
		actorRegistry.streamPlayers().forEach(player -> {
			player.setScore(0);
			player.setHealth(MAX_HEALTH);
			player.setAssignedRespawnPoint(respawnPointsIt.next());
		});

		setGameState(STATE_PLAYING);

		sendPlayerValuesSnapshotToAll(true);
		var startGameMessage = Messaging.eventStartGameToBytes(teamPlay, timeLimitMinutes);
		actorRegistry.streamPlayers().forEach(player -> {
			udpServer.sendBytesToClient(player.getClientIp(), startGameMessage);
		});

		adminConsole.refreshTable();
		adminConsole.getIndicatorGameTime().setEditable(false);
		adminConsole.getIndicatorFragLimit().setEditable(false);
		adminConsole.getGameTypeTeam().setEnabled(false);
	}

	@Override
	public void eventConsoleEndGame() {
		setGameState(STATE_IDLE);
		adminConsole.getIndicatorGameTime().setText(timeLimitMinutes+"");
		adminConsole.getIndicatorGameTime().setEditable(true);
		adminConsole.getIndicatorFragLimit().setText(fragLimit+"");
		adminConsole.getIndicatorFragLimit().setEditable(true);
		adminConsole.getGameTypeTeam().setEnabled(true);

		Player leadPlayer = actorRegistry.getLeadPlayer();
		int leadTeam = actorRegistry.getLeadTeam();
		int winner = teamPlay ? leadTeam : Optional.ofNullable(leadPlayer).map(Player::getId).orElse(-1);
		for (Player player : actorRegistry.getPlayers()) {
			udpServer.sendEventToClient(Messaging.GAME_OVER, player, winner);
		}
		sendPlayerValuesSnapshotToAll(false);
	}

	@Override
	public void refreshConsoleTable() {
		adminConsole.refreshTable();
		sendPlayerValuesSnapshotToAll(true);
	}

	@Override
	public void onPlayerDataUpdated(Player player, boolean isNameUpdated) {
		sendPlayerValuesSnapshotToAll(isNameUpdated);
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
		}
	}

	private void setGameState(int newState) {
		if (gameState != newState) {
			gameState = newState;
			adminConsole.getIndicatorStatus().setText(switch (gameState) {
				case STATE_IDLE -> "Idle";
				case STATE_PLAYING -> "Playing";
				default -> "Unknown";
			});
		}
	}

	private void sendPlayerValuesSnapshotToAll(boolean includeNames) {
		udpServer.sendStatsToAll(includeNames, gameState == STATE_PLAYING, teamPlay, timeLeftSeconds);
	}

}
