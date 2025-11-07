package net.lasertag.lasertagserver.web;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.lasertag.lasertagserver.core.ActorRegistry;
import net.lasertag.lasertagserver.core.GameEventsListener;
import net.lasertag.lasertagserver.model.Actor;
import net.lasertag.lasertagserver.model.Dispenser;
import net.lasertag.lasertagserver.model.Player;
import net.lasertag.lasertagserver.ui.WebAdminConsole;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
public class GameController {

	private final ActorRegistry actorRegistry;
	private final GameEventsListener gameEventsListener;
	private final SseEventService sseEventService;
	private final WebAdminConsole webAdminConsole;

	public GameController(ActorRegistry actorRegistry, GameEventsListener gameEventsListener, 
						  SseEventService sseEventService, WebAdminConsole webAdminConsole) {
		this.actorRegistry = actorRegistry;
		this.gameEventsListener = gameEventsListener;
		this.sseEventService = sseEventService;
		this.webAdminConsole = webAdminConsole;
	}

	@GetMapping("/events")
	public SseEmitter streamEvents() {
		SseEmitter emitter = sseEventService.createEmitter();
		
		// Send initial state immediately
		try {
			sseEventService.sendPlayersUpdate(actorRegistry.getPlayers());
			sseEventService.sendDispensersUpdate(webAdminConsole.getDispensersMap());
			sseEventService.sendTeamScoresUpdate(actorRegistry.getTeamScores());
		} catch (Exception e) {}
		
		return emitter;
	}

	@PostMapping("/game/start")
	public ResponseEntity<Map<String, String>> startGame(@RequestBody StartGameRequest request) {
		boolean teamPlay = request.isTeamPlay() && actorRegistry.getTeamScores().size() > 1;
		gameEventsListener.eventConsoleStartGame(request.getTimeLimit(), request.getFragLimit(), teamPlay);
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
		
		// Broadcast updated dispenser state to web clients
		sseEventService.sendDispensersUpdate(webAdminConsole.getDispensersMap());
		
		return ResponseEntity.ok(Map.of("status", "Dispensers updated"));
	}

	@ExceptionHandler(IOException.class)
	public void handleIOException(IOException e) {
		log.warn("Client disconnected: {}", e.getMessage());
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

