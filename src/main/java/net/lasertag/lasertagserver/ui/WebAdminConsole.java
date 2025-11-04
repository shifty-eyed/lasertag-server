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

	public void updateGameTimeStatus(int timeLeftSeconds) {
		// Send updated game state via SSE
		broadcastGameState();
	}

	public void refreshUI(boolean isPlaying) {
		// Send updated game state and players via SSE
		broadcastGameState();
		broadcastPlayers();
		broadcastDispensers();
	}

	private void broadcastGameState() {
		// This will be called from Game class, but we need the game reference
		// For now, we'll trigger it from the controller when needed
		// The actual state is fetched from Game via the controller
	}

	private void broadcastPlayers() {
		List<Player> players = actorRegistry.getPlayers();
		sseEventService.sendPlayersUpdate(players);
	}

	private void broadcastDispensers() {
		Map<String, List<Dispenser>> dispensers = new HashMap<>();
		
		List<Dispenser> healthDispensers = actorRegistry.streamByType(Actor.Type.HEALTH_DISPENSER)
			.map(actor -> (Dispenser) actor)
			.collect(Collectors.toList());
		
		List<Dispenser> ammoDispensers = actorRegistry.streamByType(Actor.Type.AMMO_DISPENSER)
			.map(actor -> (Dispenser) actor)
			.collect(Collectors.toList());
		
		dispensers.put("health", healthDispensers);
		dispensers.put("ammo", ammoDispensers);
		
		sseEventService.sendDispensersUpdate(dispensers);
	}

}

