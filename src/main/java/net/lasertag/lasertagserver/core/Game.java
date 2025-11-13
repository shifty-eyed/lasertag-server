package net.lasertag.lasertagserver.core;

import lombok.Getter;
import net.lasertag.lasertagserver.model.*;
import net.lasertag.lasertagserver.ui.WebAdminConsole;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Component
@Getter
public class Game implements GameEventsListener {
	private static final Logger log = LoggerFactory.getLogger(Game.class);

	public static final int MAX_HEALTH = 100;

	private final ActorRegistry actorRegistry;
	private final UdpServer udpServer;
	private final WebAdminConsole webAdminConsole;
	private final GameSettings gameSettings;
	private final ScheduledExecutorService scheduler =
		Executors.newScheduledThreadPool(2, new DaemonThreadFactory("DaemonScheduler"));

		
	private volatile boolean isGamePlaying = false;
	private int timeLeftSeconds = 0;

	public Game(ActorRegistry actorRegistry, UdpServer udpServer, 
				WebAdminConsole webAdminConsole, GameSettings gameSettings) {
		this.actorRegistry = actorRegistry;
		this.udpServer = udpServer;
		this.webAdminConsole = webAdminConsole;
		this.gameSettings = gameSettings;
		udpServer.setGameEventsListener(this);
		
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
				var vitalScore = gameSettings.isTeamPlay() ? actorRegistry.getTeamScores().get(hitByPlayer.getTeamId()) : hitByPlayer.getScore();
				if (vitalScore >= gameSettings.getFragLimit()) {
					eventConsoleEndGame();
				}
			} else {
				udpServer.sendEventToClient(MessageType.YOU_HIT_SOMEONE, hitByPlayer, (byte)player.getId());
			}
			sendPlayerValuesSnapshotToAll(false);
		} else if (type == MessageType.GOT_HEALTH.id()) {
			useDispenser(player, Actor.Type.HEALTH, message.getExtraValue(), MessageType.GIVE_HEALTH_TO_PLAYER);
		} else if (type == MessageType.GOT_AMMO.id()) {
			useDispenser(player, Actor.Type.AMMO, message.getExtraValue(), MessageType.GIVE_AMMO_TO_PLAYER);
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
		log.info("Starting game with timeLimitMinutes={}, fragLimit={}, teamPlay={}", timeMinutes, fragLimit, teamPlay);
		gameSettings.setTimeLimitMinutes(timeMinutes);
		gameSettings.setFragLimit(fragLimit);
		gameSettings.setTeamPlay(teamPlay);
		timeLeftSeconds = gameSettings.getTimeLimitMinutes() * 60;

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
				udpServer.sendEventToClient(MessageType.GAME_START, player, (byte) (gameSettings.isTeamPlay() ? 1 : 0), (byte) gameSettings.getTimeLimitMinutes());
			}
		});
		
	}

	@Override
	public void eventConsoleEndGame() {
		log.info("Ending game");
		setIsGamePlaying(false);

		Player leadPlayer = actorRegistry.getLeadPlayer();
		int leadTeam = actorRegistry.getLeadTeam();
		int winner = gameSettings.isTeamPlay() ? leadTeam : Optional.ofNullable(leadPlayer).map(Player::getId).orElse(-1);
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
		if (isGamePlaying) {
			timeLeftSeconds--;
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
		udpServer.sendStatsToAll(includeNames, isGamePlaying, gameSettings.isTeamPlay(), timeLeftSeconds);
	}

	private void refreshConsoleUI(boolean isPlaying) {
		if (webAdminConsole != null) {
			webAdminConsole.refreshUI(isPlaying);
		}
	}

	private void updateConsoleGameTime(int timeLeft) {
		if (webAdminConsole != null) {
			webAdminConsole.updateGameTimeLeft(timeLeft);
		}
	}


}
