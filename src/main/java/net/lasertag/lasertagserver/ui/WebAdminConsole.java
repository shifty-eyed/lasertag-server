package net.lasertag.lasertagserver.ui;

import lombok.Setter;
import net.lasertag.lasertagserver.core.ActorRegistry;
import net.lasertag.lasertagserver.core.GameEventsListener;
import net.lasertag.lasertagserver.model.Player;
import net.lasertag.lasertagserver.web.SseEventService;

import org.springframework.stereotype.Component;

import java.util.List;

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
	}

	private void broadcastPlayers() {
		List<Player> players = actorRegistry.getPlayers();
		sseEventService.sendPlayersUpdate(players);
	}

	private void broadcastDispensers() {
		sseEventService.sendDispensersUpdate(actorRegistry.getOnlineDispensers());
	}

	public void updateGameTimeLeft(int timeLeftSeconds) {
		sseEventService.sendGameTimeLeft(timeLeftSeconds);
	}

	

}

