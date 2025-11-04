package net.lasertag.lasertagserver.ui;

import lombok.Setter;
import net.lasertag.lasertagserver.core.ActorRegistry;
import net.lasertag.lasertagserver.core.GameEventsListener;
import net.lasertag.lasertagserver.model.Actor;
import net.lasertag.lasertagserver.model.Dispenser;
import net.lasertag.lasertagserver.model.Player;
import net.lasertag.lasertagserver.web.SseEventService;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class WebAdminConsole {

	private final ActorRegistry actorRegistry;
	private final SseEventService sseEventService;

	@Setter
	private GameEventsListener gameEventsListener;

	public WebAdminConsole(ActorRegistry actorRegistry, SseEventService sseEventService) {
		this.actorRegistry = actorRegistry;
		this.sseEventService = sseEventService;
	}

	public void refreshUI(boolean isPlaying) {
		sseEventService.sendGameIsPlaying(isPlaying);
		broadcastPlayers();
		broadcastDispensers();
		broadcastTeamScores();
	}

	private void broadcastPlayers() {
		List<Player> players = actorRegistry.getPlayers();
		sseEventService.sendPlayersUpdate(players);
	}

	private void broadcastTeamScores() {
		Map<Integer, Integer> teamScores = actorRegistry.getTeamScores();
		sseEventService.sendTeamScoresUpdate(teamScores);
	}

	private void broadcastDispensers() {
		sseEventService.sendDispensersUpdate(getDispensersMap());
	}

	public Map<String, List<Dispenser>> getDispensersMap() {
		Map<String, List<Dispenser>> dispensers = new HashMap<>();
		
		List<Dispenser> healthDispensers = actorRegistry.streamByType(Actor.Type.HEALTH_DISPENSER)
			.map(actor -> (Dispenser) actor)
			.collect(Collectors.toList());
		
		List<Dispenser> ammoDispensers = actorRegistry.streamByType(Actor.Type.AMMO_DISPENSER)
			.map(actor -> (Dispenser) actor)
			.collect(Collectors.toList());
		
		dispensers.put("health", healthDispensers);
		dispensers.put("ammo", ammoDispensers);
		
		return dispensers;
	}

	public void updateGameTimeLeft(int timeLeftSeconds) {
		sseEventService.sendGameTimeLeft(timeLeftSeconds);
	}

	

}

