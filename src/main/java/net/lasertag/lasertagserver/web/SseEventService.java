package net.lasertag.lasertagserver.web;

import com.fasterxml.jackson.databind.ObjectMapper;
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

	public SseEmitter createEmitter() {
		SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
		emitters.add(emitter);

		emitter.onCompletion(() -> {
			log.info("SSE emitter completed");
			emitters.remove(emitter);
		});

		emitter.onTimeout(() -> {
			log.info("SSE emitter timed out");
			emitters.remove(emitter);
		});

		emitter.onError(e -> {
			log.error("SSE emitter error", e);
			emitters.remove(emitter);
		});

		log.info("New SSE client connected. Total clients: {}", emitters.size());
		return emitter;
	}

	public void sendGameStateUpdate(Object gameState) {
		sendEvent("game-state", gameState);
	}

	public void sendPlayersUpdate(Object players) {
		sendEvent("players", players);
	}

	public void sendDispensersUpdate(Object dispensers) {
		sendEvent("dispensers", dispensers);
	}

	private void sendEvent(String eventName, Object data) {
		if (emitters.isEmpty()) {
			return;
		}

		try {
			String jsonData = objectMapper.writeValueAsString(data);
			CopyOnWriteArrayList<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

			for (SseEmitter emitter : emitters) {
				try {
					emitter.send(SseEmitter.event()
						.name(eventName)
						.data(jsonData));
				} catch (IOException e) {
					log.error("Failed to send SSE event to client", e);
					deadEmitters.add(emitter);
				}
			}

			emitters.removeAll(deadEmitters);
		} catch (Exception e) {
			log.error("Failed to serialize event data", e);
		}
	}
}

