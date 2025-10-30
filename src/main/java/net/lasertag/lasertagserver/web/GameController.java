package net.lasertag.lasertagserver.web;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.lasertag.lasertagserver.core.ActorRegistry;
import net.lasertag.lasertagserver.core.Game;
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
	private final Game game;
	private final SseEventService sseEventService;

	public GameController(ActorRegistry actorRegistry, GameEventsListener gameEventsListener, 
						  Game game, SseEventService sseEventService) {
		this.actorRegistry = actorRegistry;
		this.gameEventsListener = gameEventsListener;
		this.game = game;
		this.sseEventService = sseEventService;
	}

	@GetMapping("/events")
	public SseEmitter streamEvents() {
		SseEmitter emitter = sseEventService.createEmitter();
		
		// Send initial state immediately
		try {
			sseEventService.sendGameStateUpdate(getGameState());
			sseEventService.sendPlayersUpdate(getPlayers());
			sseEventService.sendDispensersUpdate(getDispensers());
		} catch (Exception e) {
			// Ignore, will be sent on next update
		}
		
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

	@GetMapping("/game/state")
	public ResponseEntity<GameStateDto> getGameState() {
		return ResponseEntity.ok(new GameStateDto(
			game.isGamePlaying(),
			game.getTimeLeftSeconds(),
			game.isTeamPlay(),
			game.getFragLimit(),
			game.getTimeLimitMinutes(),
			actorRegistry.getTeamScores()
		));
	}

	@GetMapping("/players")
	public ResponseEntity<List<PlayerDto>> getPlayers() {
		List<PlayerDto> players = actorRegistry.getPlayers().stream()
			.map(this::toPlayerDto)
			.collect(Collectors.toList());
		return ResponseEntity.ok(players);
	}

	@PutMapping("/players/{id}")
	public ResponseEntity<PlayerDto> updatePlayer(@PathVariable int id, @RequestBody UpdatePlayerRequest request) {
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
		
		return ResponseEntity.ok(toPlayerDto(player));
	}

	@GetMapping("/dispensers")
	public ResponseEntity<Map<String, List<DispenserDto>>> getDispensers() {
		Map<String, List<DispenserDto>> result = new HashMap<>();
		
		List<DispenserDto> healthDispensers = actorRegistry.streamByType(Actor.Type.HEALTH_DISPENSER)
			.map(actor -> toDispenserDto((Dispenser) actor))
			.collect(Collectors.toList());
		
		List<DispenserDto> ammoDispensers = actorRegistry.streamByType(Actor.Type.AMMO_DISPENSER)
			.map(actor -> toDispenserDto((Dispenser) actor))
			.collect(Collectors.toList());
		
		result.put("health", healthDispensers);
		result.put("ammo", ammoDispensers);
		
		return ResponseEntity.ok(result);
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

	private PlayerDto toPlayerDto(Player player) {
		return new PlayerDto(
			player.getId(),
			player.getName(),
			player.getScore(),
			player.getHealth(),
			player.getBulletsMax(),
			player.getDamage(),
			player.getTeamId(),
			player.getAssignedRespawnPoint(),
			player.isOnline()
		);
	}

	private DispenserDto toDispenserDto(Dispenser dispenser) {
		return new DispenserDto(
			dispenser.getId(),
			dispenser.getType().name(),
			dispenser.getAmount(),
			dispenser.getDispenseTimeoutSec(),
			dispenser.isOnline()
		);
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

	@Getter
	@AllArgsConstructor
	public static class GameStateDto {
		private boolean playing;
		private int timeLeftSeconds;
		private boolean teamPlay;
		private int fragLimit;
		private int timeLimitMinutes;
		private Map<Integer, Integer> teamScores;
	}

	@Getter
	@AllArgsConstructor
	public static class PlayerDto {
		private int id;
		private String name;
		private int score;
		private int health;
		private int bulletsMax;
		private int damage;
		private int teamId;
		private int assignedRespawnPoint;
		private boolean online;
	}

	@Getter
	@AllArgsConstructor
	public static class DispenserDto {
		private int id;
		private String type;
		private int amount;
		private int dispenseTimeoutSec;
		private boolean online;
	}
}

