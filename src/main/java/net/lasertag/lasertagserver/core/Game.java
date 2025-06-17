package net.lasertag.lasertagserver.core;

import lombok.Getter;
import net.lasertag.lasertagserver.model.*;
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
		if (player.updateHealth(message.getHealth())) {
			sendPlayerValuesSnapshotToAll(false);
		}
		var type = message.getTypeId();
		if (type == MessageType.GOT_HIT.id() || type == MessageType.YOU_KILLED.id())  {
			var hitByPlayer = actorRegistry.getPlayerById(message.getExtraValue());
			if (type == MessageType.YOU_KILLED.id()) {
				hitByPlayer.setScore(hitByPlayer.getScore() + 1);
				udpServer.sendEventToClient(MessageType.YOU_SCORED, hitByPlayer, (byte)player.getId());
				player.setAssignedRespawnPoint(actorRegistry.getRandomRespawnPointId());
				var vitalScore = teamPlay ? actorRegistry.getTeamScores().get(hitByPlayer.getTeamId()) : hitByPlayer.getScore();
				if (vitalScore >= fragLimit) {
					eventConsoleEndGame();
				}
			} else {
				udpServer.sendEventToClient(MessageType.YOU_HIT_SOMEONE, hitByPlayer, (byte)player.getId());
			}
			sendPlayerValuesSnapshotToAll(false);
		} else if (type == MessageType.GOT_HEALTH.id()) {
			useDispenser(player, Actor.Type.HEALTH_DISPENSER, message.getExtraValue(), MessageType.GIVE_HEALTH_TO_PLAYER);
		} else if (type == MessageType.GOT_AMMO.id()) {
			useDispenser(player, Actor.Type.AMMO_DISPENSER, message.getExtraValue(), MessageType.GIVE_AMMO_TO_PLAYER);
		}

		adminConsole.refreshTable();
	}

	private void useDispenser(Player player, Actor.Type dispenserType, int dispenserId, MessageType messageToPlayerType) {
		var dispenser = (Dispenser) actorRegistry.getActorByTypeAndId(dispenserType, dispenserId);
		udpServer.sendEventToClient(MessageType.DISPENSER_USED, dispenser);
		var amount = Math.min(dispenser.getAmount(), player.getMaxHealth() - player.getHealth());
		udpServer.sendEventToClient(messageToPlayerType, player, (byte)amount);
	}

	@Override
	public void eventConsoleStartGame() {
		timeLimitMinutes = Integer.parseInt(adminConsole.getIndicatorGameTime().getText());
		fragLimit = Integer.parseInt(adminConsole.getIndicatorFragLimit().getText());
		teamPlay = adminConsole.getGameTypeTeam().isSelected() && actorRegistry.getTeamScores().size() > 1;
		timeLeftSeconds = timeLimitMinutes * 60;

		var respawnPointsIt = actorRegistry.shuffledRespawnPointIds().iterator();
		actorRegistry.streamPlayers().forEach(player -> {
			player.setScore(0);
			player.setHealth(0);
			//player.setAssignedRespawnPoint(respawnPointsIt.next());
			player.setAssignedRespawnPoint(0);
		});

		setGameState(STATE_PLAYING);
		sendPlayerValuesSnapshotToAll(true);
		actorRegistry.streamPlayers().forEach(player -> {
			if (player.isOnline()) {
				udpServer.sendEventToClient(MessageType.GAME_START, player, (byte) (teamPlay ? 1 : 0), (byte) timeLimitMinutes);
			}
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
			udpServer.sendEventToClient(MessageType.GAME_OVER, player, (byte)winner);
		}
		sendPlayerValuesSnapshotToAll(false);
	}

	@Override
	public void refreshConsoleTable() {
		adminConsole.refreshTable();
	}

	@Override
	public void onPlayerJoinedOrLeft() {
		sendPlayerValuesSnapshotToAll(true);
	}

	@Override
	public void onPlayerDataUpdated(Player player, boolean isNameUpdated) {
		sendPlayerValuesSnapshotToAll(isNameUpdated);
	}

	@Override
	public void onDispenserSettingsUpdated() {
		udpServer.sendSettingsToAllDispensers();
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
