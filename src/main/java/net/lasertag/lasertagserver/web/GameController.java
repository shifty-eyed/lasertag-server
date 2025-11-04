package net.lasertag.lasertagserver.web;

import lombok.Getter;
import lombok.Setter;
import net.lasertag.lasertagserver.core.ActorRegistry;
import net.lasertag.lasertagserver.core.GameEventsListener;
import net.lasertag.lasertagserver.model.Actor;
import net.lasertag.lasertagserver.model.Dispenser;
import net.lasertag.lasertagserver.model.Player;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class GameController {

	private final ActorRegistry actorRegistry;
	private final GameEventsListener gameEventsListener;
	private final SseEventService sseEventService;

	public GameController(ActorRegistry actorRegistry, GameEventsListener gameEventsListener, SseEventService sseEventService) {
		this.actorRegistry = actorRegistry;
		this.gameEventsListener = gameEventsListener;
		this.sseEventService = sseEventService;
	}

	@GetMapping("/events")
	public SseEmitter streamEvents() {
		SseEmitter emitter = sseEventService.createEmitter();
		
		// Send initial state immediately
		try {
			sseEventService.sendPlayersUpdate(getPlayersList());
			sseEventService.sendDispensersUpdate(getDispensersMap());
			sseEventService.sendTeamScoresUpdate(actorRegistry.getTeamScores());
		} catch (Exception e) {}
		
		return emitter;
	}

	@PostMapping("/game/start")
	public ResponseEntity<Map<String, String>> startGame(@RequestBody StartGameRequest request) {
		gameEventsListener.eventConsoleStartGame(request.getTimeLimit(), request.getFragLimit(), request.isTeamPlay());
		return ResponseEntity.ok(Map.of("status", "Game started"));
	}

	@PostMapping("/game/end")
	public ResponseEntity<Map<String, String>> endGame() {
		gameEventsListener.eventConsoleEndGame();
		return ResponseEntity.ok(Map.of("status", "Game ended"));
	}

	@PutMapping("/players/{id}")
	public ResponseEntity<Player> updatePlayer(@PathVariable int id, @RequestBody UpdatePlayerRequest request) {
		Player player = actorRegistry.getPlayerById(id);
		
		boolean nameUpdated = false;
		if (request.getName() != null) {
			player.setName(request.getName());
			nameUpdated = true;
		}
		if (request.getTeamId() != null) {
			player.setTeamId(request.getTeamId());
		}
		if (request.getDamage() != null) {
			player.setDamage(request.getDamage());
		}
		if (request.getBulletsMax() != null) {
			player.setBulletsMax(request.getBulletsMax());
		}
		
		gameEventsListener.onPlayerDataUpdated(player, nameUpdated);
		
		return ResponseEntity.ok(player);
	}

	@PutMapping("/dispensers/{type}")
	public ResponseEntity<Map<String, String>> updateDispensers(
		@PathVariable String type, 
		@RequestBody UpdateDispenserRequest request
	) {
		Actor.Type dispenserType = type.equalsIgnoreCase("health") 
			? Actor.Type.HEALTH_DISPENSER 
			: Actor.Type.AMMO_DISPENSER;
		
		actorRegistry.streamByType(dispenserType).forEach(actor -> {
			Dispenser dispenser = (Dispenser) actor;
			if (request.getTimeout() != null && request.getTimeout() > 0) {
				dispenser.setDispenseTimeoutSec(request.getTimeout());
			}
			if (request.getAmount() != null && request.getAmount() > 0) {
				dispenser.setAmount(request.getAmount());
			}
		});
		
		gameEventsListener.onDispenserSettingsUpdated();
		
		return ResponseEntity.ok(Map.of("status", "Dispensers updated"));
	}

	private List<Player> getPlayersList() {
		return actorRegistry.getPlayers();
	}

	private Map<String, List<Dispenser>> getDispensersMap() {
		Map<String, List<Dispenser>> result = new HashMap<>();
		
		List<Dispenser> healthDispensers = actorRegistry.streamByType(Actor.Type.HEALTH_DISPENSER)
			.map(actor -> (Dispenser) actor)
			.collect(Collectors.toList());
		
		List<Dispenser> ammoDispensers = actorRegistry.streamByType(Actor.Type.AMMO_DISPENSER)
			.map(actor -> (Dispenser) actor)
			.collect(Collectors.toList());
		
		result.put("health", healthDispensers);
		result.put("ammo", ammoDispensers);
		
		return result;
	}

	// DTOs
	@Getter
	@Setter
	public static class StartGameRequest {
		private int timeLimit;
		private int fragLimit;
		private boolean teamPlay;
	}

	@Getter
	@Setter
	public static class UpdatePlayerRequest {
		private String name;
		private Integer teamId;
		private Integer damage;
		private Integer bulletsMax;
	}

	@Getter
	@Setter
	public static class UpdateDispenserRequest {
		private Integer timeout;
		private Integer amount;
	}

}

