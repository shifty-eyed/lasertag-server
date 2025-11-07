package net.lasertag.lasertagserver.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class SseEventService {

	private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@PostConstruct
	public void init() {
		// Register this service with the SseLogAppender
		SseLogAppender.setSseEventService(this);
	}

	public SseEmitter createEmitter() {
		SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
		emitters.add(emitter);

		emitter.onCompletion(() -> {
			removeEmitter(emitter);
		});

		emitter.onTimeout(() -> {
			removeEmitter(emitter);
		});

		emitter.onError(e -> {
			removeEmitter(emitter);
		});

		log.info("New SSE client connected. Total clients: {}", emitters.size());
		return emitter;
	}

	private void removeEmitter(SseEmitter emitter) {
		emitters.remove(emitter);
	}

	public void sendGameIsPlaying(boolean isPlaying) {
		sendEvent("isPlaying", isPlaying);
	}

	public void sendGameTimeLeft(int timeLeft) {
		sendEvent("timeLeft", timeLeft);
	}

	public void sendPlayersUpdate(Object players) {
		sendEvent("players", players);
	}

	public void sendDispensersUpdate(Object dispensers) {
		sendEvent("dispensers", dispensers);
	}

	public void sendTeamScoresUpdate(Object teamScores) {
		sendEvent("teamScores", teamScores);
	}

	public void sendLogMessage(String logMessage) {
		sendEvent("log", logMessage);
	}

	private void sendEvent(String eventName, Object data) {
		if (emitters.isEmpty()) {
			return;
		}
		log.info("Sending SSE event: {} with data: {}", eventName, data.toString());
		try {
			String jsonData = objectMapper.writeValueAsString(data);
			CopyOnWriteArrayList<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

			for (SseEmitter emitter : emitters) {
				try {
					emitter.send(SseEmitter.event()
						.name(eventName)
						.data(jsonData));
				} catch (IOException e) {
					log.debug("Failed to send SSE event to client (disconnected): {}", e.getMessage());
					deadEmitters.add(emitter);
				}
			}

			emitters.removeAll(deadEmitters);
		} catch (Exception e) {
			log.error("Failed to serialize event data", e);
		}
	}
}

