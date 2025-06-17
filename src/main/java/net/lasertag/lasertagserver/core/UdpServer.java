package net.lasertag.lasertagserver.core;

import jakarta.annotation.PostConstruct;
import lombok.Setter;
import net.lasertag.lasertagserver.model.*;

import static net.lasertag.lasertagserver.model.Messaging.*;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Component
public class UdpServer {

	@Setter
	private GameEventsListener gameEventsListener;

	private final ActorRegistry actorRegistry;
	private final List<Long> lastPingTime;

	private final long pingTimeout = 5000;

	private volatile boolean running = true;
	private final int port;
	private final int devicePort;

	private final ThreadPoolTaskExecutor daemonExecutor;

	public UdpServer(ActorRegistry actorRegistry, ThreadPoolTaskExecutor daemonExecutor) {
		this.port = 9878;
		this.devicePort = 1234;
		this.actorRegistry = actorRegistry;
		this.daemonExecutor = daemonExecutor;

		this.lastPingTime = new ArrayList<>(actorRegistry.getPlayers().size());
		for (int i = 0; i < actorRegistry.getPlayers().size() + 1; i++) {
			lastPingTime.add(0L);
		}
	}

	@PostConstruct
	public void init() {
		daemonExecutor.execute(this::startUdpServer);
		Runtime.getRuntime().addShutdownHook(new Thread(this::stopUdpServer));
	}

	private void startUdpServer() {
		try (DatagramSocket serverSocket = new DatagramSocket(port)) {
			serverSocket.setSoTimeout(1000);
			System.out.printf("%s started on port: %d thread: %s\n",
				this.getClass().getSimpleName(), port, Thread.currentThread().getName());
			byte[] receiveBuffer = new byte[64];
			while (running) {
				DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
				try {
					serverSocket.receive(receivePacket);
					processPacketFromClient(receivePacket);
				} catch (SocketTimeoutException ignored) {}
			}
			System.out.println(this.getClass().getSimpleName() + ": UDP Server stopped");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void sendAckToClient(InetAddress ip) {
		try (DatagramSocket clientSocket = new DatagramSocket()) {
			byte[] sendData = new byte[] {MessageType.PING.id()};
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ip, devicePort);
			clientSocket.send(sendPacket);
		} catch (Exception e) {
			System.out.println("Error sending command to client: " + e.getMessage());
		}
	}

	private void sendBytesToClient(InetAddress ip, byte[] bytes) {
		if (ip == null) {
			return;
		}
		try (DatagramSocket clientSocket = new DatagramSocket()) {
			DatagramPacket sendPacket = new DatagramPacket(bytes, bytes.length, ip, devicePort);
			clientSocket.send(sendPacket);
			System.out.printf("Bytes to %s:%d len=%d, data: %s\n",
				sendPacket.getAddress(), sendPacket.getPort(), sendPacket.getLength(), Arrays.toString(sendPacket.getData()));
		} catch (Exception e) {
			System.out.println("Error sending command to client: " + e.getMessage());
		}
	}

	public void stopUdpServer() {
		running = false;
		daemonExecutor.shutdown();
		System.out.println(this.getClass().getSimpleName() + ": Stopping UDP Server...");
	}

	private void processPacketFromClient(DatagramPacket packet) {
		try {
			var message = new MessageFromClient(packet.getData(), packet.getLength());
			var actor = actorRegistry.getActorByMessage(message);
			if (actor.getClientIp() == null || message.isFirstEverMessage()) {
				actor.setClientIp(packet.getAddress());
				System.out.printf("%s %d connected\n", actor.getType().name(), actor.getId());
				gameEventsListener.refreshConsoleTable();
			}
			lastPingTime.set(message.getActorId(), System.currentTimeMillis());
			if (PING_GROUP.contains(message.getTypeId())) {
				sendAckToClient(actor.getClientIp());
			} else {
				System.out.printf("Event %s from %s len=%d, data: %s\n", message.getType().name(), actor,	packet.getLength(), message);
				gameEventsListener.onMessageFromPlayer((Player)actor, message);
			}
		} catch (Exception e) {
			System.out.println("Error parsing message: " + e.getMessage());
		}
	}

	@Scheduled(fixedDelay = 1000)
	private void checkConnectedClients() {
		var currentTime = System.currentTimeMillis();
		actorRegistry.getPlayers().forEach(player -> {
			var lastPing = lastPingTime.get(player.getId());
			if (currentTime - lastPing > pingTimeout) {
				if (player.getClientIp() != null) {
					System.out.printf("%s lost connection to player %d\n", this.getClass().getSimpleName(), player.getId());
					player.setClientIp(null);
					if (gameEventsListener != null) {
						gameEventsListener.refreshConsoleTable();
					}
				}
			}
		});
	}

	public void sendEventToClient(MessageType type, Actor actor, byte... values) {
		System.out.printf("Event to %s: type=%s, data: %s\n", actor.toString(), type.name(), Arrays.toString(values));
		var bytes = Messaging.eventToBytes(type.id(), values);
		sendBytesToClient(actor.getClientIp(), bytes);
	}

	public void sendStatsToAll(boolean includeNames, boolean isGameRunning, boolean teamPlay, int timeSeconds) {
		var bytes = Messaging.playerStatsToBytes(includeNames, actorRegistry.getPlayersSortedByScore(), isGameRunning, teamPlay, timeSeconds);
		actorRegistry.getPlayers().forEach(player -> sendBytesToClient(player.getClientIp(), bytes));
	}

	public void sendSettingsToAllDispensers() {
		Stream.concat(actorRegistry.streamByType(Actor.Type.AMMO_DISPENSER), actorRegistry.streamByType(Actor.Type.HEALTH_DISPENSER)).forEach(actor -> {
			var dispenser = (Dispenser) actor;
			sendEventToClient(MessageType.DISPENSER_SET_TIMEOUT, dispenser, (byte)(dispenser.getDispenseTimeoutSec() / 10));// to pack as 1 byte
		});
	}

}
