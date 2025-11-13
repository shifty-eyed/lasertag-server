package net.lasertag.lasertagserver.web;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.lasertag.lasertagserver.core.ActorRegistry;
import net.lasertag.lasertagserver.core.GameEventsListener;
import net.lasertag.lasertagserver.core.GameSettings;
import net.lasertag.lasertagserver.model.Actor;
import net.lasertag.lasertagserver.model.Player;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api")
@Slf4j
public class GameController {

	private final ActorRegistry actorRegistry;
	private final GameEventsListener gameEventsListener;
	private final SseEventService sseEventService;
	private final GameSettings gameSettings;

	public GameController(ActorRegistry actorRegistry, GameEventsListener gameEventsListener, 
						  SseEventService sseEventService, GameSettings gameSettings) {
		this.actorRegistry = actorRegistry;
		this.gameEventsListener = gameEventsListener;
		this.sseEventService = sseEventService;
		this.gameSettings = gameSettings;
	}

	@GetMapping("/events")
	public SseEmitter streamEvents() {
		SseEmitter emitter = sseEventService.createEmitter();
		
		// Send initial state immediately
		try {
			sseEventService.sendPlayersUpdate(actorRegistry.getPlayers());
			sseEventService.sendDispensersUpdate(actorRegistry.getOnlineDispensers());
			// Send initial settings
			sseEventService.sendSettingsUpdate(gameSettings.getAllSettings());
		} catch (Exception e) {}
		
		return emitter;
	}

	@GetMapping("/settings")
	public ResponseEntity<Map<String, Object>> getSettings() {
		return ResponseEntity.ok(gameSettings.getAllSettings());
	}

	@PostMapping("/game/start")
	public ResponseEntity<Map<String, String>> startGame(@RequestBody StartGameRequest request) {
		gameSettings.setTimeLimitMinutes(request.getTimeLimit());
		gameSettings.setFragLimit(request.getFragLimit());
		boolean teamPlay = request.isTeamPlay() && actorRegistry.getTeamScores().size() > 1;
		gameSettings.setTeamPlay(teamPlay);
		gameSettings.syncToActors(actorRegistry);
		gameEventsListener.eventConsoleStartGame(request.getTimeLimit(), request.getFragLimit(), teamPlay);
		return ResponseEntity.ok(Map.of("status", "Game started"));
	}

	@PostMapping("/game/end")
	public ResponseEntity<Map<String, String>> endGame() {
		gameEventsListener.eventConsoleEndGame();
		return ResponseEntity.ok(Map.of("status", "Game ended"));
	}

	@PutMapping("/players/{id}")
	public ResponseEntity<Player> updatePlayer(@PathVariable int id, @RequestBody GameSettings.PlayerSettings request) {
		GameSettings.PlayerSettings existingSettings = gameSettings.getPlayerSettings(id);
		boolean nameUpdated = existingSettings != null && !Objects.equals(existingSettings.getName(), request.getName());

		gameSettings.setPlayerSettings(id, request);
		gameSettings.syncToActors(actorRegistry);

		Player player = actorRegistry.getPlayerById(id);
		gameEventsListener.onPlayerDataUpdated(player, nameUpdated);
		
		return ResponseEntity.ok(player);
	}

	@PutMapping("/dispensers/{type}")
	public ResponseEntity<Map<String, String>> updateDispensers(
		@PathVariable String type, 
		@RequestBody UpdateDispenserRequest request
	) {
		Actor.Type dispenserType = switch (type.toLowerCase()) {
			case "health" -> Actor.Type.HEALTH;
			case "ammo" -> Actor.Type.AMMO;
			default -> throw new IllegalArgumentException("Unknown dispenser type: " + type);
		};
		
		if (request.getTimeout() != null && request.getTimeout() > 0) {
			gameSettings.setDispenserTimeout(dispenserType, request.getTimeout());
		}
		if (request.getAmount() != null && request.getAmount() > 0) {
			gameSettings.setDispenserAmount(dispenserType, request.getAmount());
		}
		
		gameSettings.syncToActors(actorRegistry);
		gameEventsListener.onDispenserSettingsUpdated();
		
		// Broadcast updated dispenser state to web clients
		sseEventService.sendDispensersUpdate(actorRegistry.getOnlineDispensers());
		
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
	public static class UpdateDispenserRequest {
		private Integer timeout;
		private Integer amount;
	}

}

