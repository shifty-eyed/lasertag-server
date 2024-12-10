package net.lasertag.lasertagserver.core;

import jakarta.annotation.PostConstruct;
import lombok.Setter;
import net.lasertag.lasertagserver.model.Messaging;
import static net.lasertag.lasertagserver.model.Messaging.*;
import net.lasertag.lasertagserver.model.Player;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class UdpServer {

	@Setter
	private GameEventsListener gameEventsListener;

	private final PlayerRegistry playerRegistry;
	private final List<Long> lastPingTime;

	private final long pingTimeout = 5000;

	private volatile boolean running = true;
	private final int port;
	private final int devicePort;

	private final ThreadPoolTaskExecutor daemonExecutor;

	public UdpServer(PlayerRegistry playerRegistry, ThreadPoolTaskExecutor daemonExecutor) {
		this.port = 9878;
		this.devicePort = 1234;
		this.playerRegistry = playerRegistry;
		this.daemonExecutor = daemonExecutor;

		this.lastPingTime = new ArrayList<>(playerRegistry.getPlayers().size());
		for (int i = 0; i < playerRegistry.getPlayers().size() + 1; i++) {
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

	private void sendAckToClient(Player player) {
		try (DatagramSocket clientSocket = new DatagramSocket()) {
			byte[] sendData = new byte[] { PING };
			var ip = player.getClientIp();
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ip, devicePort);
			clientSocket.send(sendPacket);
		} catch (Exception e) {
			System.out.println("Error sending command to client: " + e.getMessage());
		}
	}

	public void sendBytesToClient(Player player, byte[] bytes) {
		var ip = player.getClientIp();
		if (ip == null) {
			return;
		}
		try (DatagramSocket clientSocket = new DatagramSocket()) {
			DatagramPacket sendPacket = new DatagramPacket(bytes, bytes.length, ip, devicePort);
			clientSocket.send(sendPacket);
			System.out.printf("Command to client %s:%d len=%d, data: %s\n",
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
			var player = playerRegistry.getPlayerById(message.getPlayerId());
			if (player.getClientIp() == null || message.isFirstEverMessage()) {
				player.setClientIp(packet.getAddress());
				System.out.printf("Player %d connected\n", player.getId());
				gameEventsListener.deviceConnected(player);
			}
			lastPingTime.set(message.getPlayerId(), System.currentTimeMillis());
			gameEventsListener.onMessageFromClient(message);
			sendAckToClient(playerRegistry.getPlayerById(message.getPlayerId()));
		} catch (Exception e) {
			System.out.println("Error parsing message: " + e.getMessage());
		}
	}

	@Scheduled(fixedDelay = 1000)
	private void checkConnectedClients() {
		var currentTime = System.currentTimeMillis();
		playerRegistry.getPlayers().forEach(player -> {
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

	public void sendEventToPhone(byte type, Player player, int extraValue) {
		var bytes = Messaging.eventToBytes(type, extraValue);
		sendBytesToClient(player, bytes);
	}

	public void sendStatsToAll(boolean isGameRunning, boolean teamPlay) {
		var bytes = Messaging.playerStatsToBytes(playerRegistry.getPlayersSortedByScore(), isGameRunning, teamPlay);
		playerRegistry.getPlayers().forEach(player -> sendBytesToClient(player, bytes));
	}

	public void sendTimeCorrectionToPlayer(Player player, int munites, int seconds) {
		var bytes = Messaging.timeCorrectionToBytes(munites, seconds);
		sendBytesToClient(player, bytes);
	}

}
