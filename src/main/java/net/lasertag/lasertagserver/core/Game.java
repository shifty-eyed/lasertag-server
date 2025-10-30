package net.lasertag.lasertagserver.core;

import lombok.Getter;
import net.lasertag.lasertagserver.model.*;
import net.lasertag.lasertagserver.ui.AdminConsole;
import net.lasertag.lasertagserver.ui.WebAdminConsole;
import net.lasertag.lasertagserver.web.SseEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Component
@Getter
public class Game implements GameEventsListener {
	public static final int MAX_HEALTH = 100;

	private final ActorRegistry actorRegistry;
	private final UdpServer udpServer;
	private final AdminConsole adminConsole;
	private final WebAdminConsole webAdminConsole;
	private final SseEventService sseEventService;
	private final ScheduledExecutorService scheduler =
		Executors.newScheduledThreadPool(2, new DaemonThreadFactory("DaemonScheduler"));

		
	private volatile boolean isGamePlaying = false;
	private int fragLimit;
	private boolean teamPlay = false;
	private int timeLimitMinutes;
	private int timeLeftSeconds = 0;

	public Game(ActorRegistry actorRegistry, UdpServer udpServer, 
				@Autowired(required = false) AdminConsole adminConsole,
				@Autowired(required = false) WebAdminConsole webAdminConsole,
				@Autowired(required = false) SseEventService sseEventService) {
		this.actorRegistry = actorRegistry;
		this.udpServer = udpServer;
		this.adminConsole = adminConsole;
		this.webAdminConsole = webAdminConsole;
		this.sseEventService = sseEventService;
		udpServer.setGameEventsListener(this);
		
		if (adminConsole != null) {
			adminConsole.setGameEventsListener(this);
		}
		if (webAdminConsole != null) {
			webAdminConsole.setGameEventsListener(this);
		}
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

		refreshConsoleUI(isGamePlaying);
	}

	private void useDispenser(Player player, Actor.Type dispenserType, int dispenserId, MessageType messageToPlayerType) {
		var dispenser = (Dispenser) actorRegistry.getActorByTypeAndId(dispenserType, dispenserId);
		udpServer.sendEventToClient(MessageType.DISPENSER_USED, dispenser);
		udpServer.sendEventToClient(messageToPlayerType, player, (byte)dispenser.getAmount());
	}

	@Override
	public void eventConsoleStartGame(int timeMinutes, int fragLimit, boolean teamPlay) {
		this.timeLimitMinutes = timeMinutes;
		this.fragLimit = fragLimit;
		this.teamPlay = teamPlay;
		timeLeftSeconds = timeLimitMinutes * 60;

		var respawnPointsIt = actorRegistry.shuffledRespawnPointIds().iterator();
		actorRegistry.streamPlayers().forEach(player -> {
			player.setScore(0);
			player.setHealth(0);
			player.setAssignedRespawnPoint(respawnPointsIt.next());
		});

		setIsGamePlaying(true);
		sendPlayerValuesSnapshotToAll(true);
		actorRegistry.streamPlayers().forEach(player -> {
			if (player.isOnline()) {
				udpServer.sendEventToClient(MessageType.GAME_START, player, (byte) (teamPlay ? 1 : 0), (byte) timeLimitMinutes);
			}
		});
		
	}

	@Override
	public void eventConsoleEndGame() {
		setIsGamePlaying(false);

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
		refreshConsoleUI(isGamePlaying);
	}

	@Override
	public void onPlayerJoinedOrLeft() {
		sendPlayerValuesSnapshotToAll(true);
	}

	@Override
	public void onPlayerDataUpdated(Player player, boolean isNameUpdated) {
		sendPlayerValuesSnapshotToAll(isNameUpdated);
		refreshConsoleUI(isGamePlaying);
	}

	@Override
	public void onDispenserSettingsUpdated() {
		udpServer.sendSettingsToAllDispensers();
	}

	@Scheduled(fixedDelay = 1000, initialDelay = 1000)
	public void updateGameTime() {
		timeLeftSeconds--;
		if (isGamePlaying) {
			if (timeLeftSeconds <= 0) {
				eventConsoleEndGame();
				return;
			}
			updateConsoleGameTime(timeLeftSeconds);
		}
	}

	private void setIsGamePlaying(boolean newState) {
		if (isGamePlaying != newState) {
			isGamePlaying = newState;
			refreshConsoleUI(newState);
		}
	}

	private void sendPlayerValuesSnapshotToAll(boolean includeNames) {
		udpServer.sendStatsToAll(includeNames, isGamePlaying, teamPlay, timeLeftSeconds);
	}

	private void refreshConsoleUI(boolean isPlaying) {
		if (adminConsole != null) {
			adminConsole.refreshUI(isPlaying);
		}
		if (webAdminConsole != null) {
			webAdminConsole.refreshUI(isPlaying);
		}
		broadcastGameState();
	}

	private void updateConsoleGameTime(int timeLeft) {
		if (adminConsole != null) {
			adminConsole.updateGameTimeStatus(timeLeft);
		}
		if (webAdminConsole != null) {
			webAdminConsole.updateGameTimeStatus(timeLeft);
		}
		broadcastGameState();
	}

	private void broadcastGameState() {
		if (sseEventService != null) {
			sseEventService.sendGameStateUpdate(new GameStateUpdate(
				isGamePlaying, timeLeftSeconds, teamPlay, fragLimit, 
				timeLimitMinutes, actorRegistry.getTeamScores()
			));
		}
	}

	// DTO for SSE game state updates
	private record GameStateUpdate(
		boolean playing,
		int timeLeftSeconds,
		boolean teamPlay,
		int fragLimit,
		int timeLimitMinutes,
		java.util.Map<Integer, Integer> teamScores
	) {}

}
