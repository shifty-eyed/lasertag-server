package net.lasertag.lasertagserver.core;

import lombok.Setter;
import net.lasertag.lasertagserver.model.MessageFromDevice;
import net.lasertag.lasertagserver.model.Player;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

public abstract class AbstractUdpServer {

	@Setter
	protected GameEventsListener gameEventsListener;

	protected final PlayerRegistry playerRegistry;
	private final List<Long> lastPingTime;

	private long pingTimeout = 5000;

	private volatile boolean running = true;
	private final int port;
	private final int devicePort;

	protected final ThreadPoolTaskExecutor daemonExecutor;

	public AbstractUdpServer(int port, int devicePort, PlayerRegistry playerRegistry, ThreadPoolTaskExecutor daemonExecutor) {
		this.port = port;
		this.devicePort = devicePort;
		this.playerRegistry = playerRegistry;
		this.daemonExecutor = daemonExecutor;

		this.lastPingTime = new ArrayList<>(playerRegistry.getPlayers().size());
		for (int i = 0; i < playerRegistry.getPlayers().size() + 1; i++) {
			lastPingTime.add(0L);
		}
	}

	protected abstract InetAddress getDeviceIp(Player player);
	protected abstract void setDeviceIp(Player player, InetAddress ip);
	protected abstract void onMessageReceived(MessageFromDevice message);

	public void startUdpServer() {
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
			byte[] sendData = new byte[] { 1 };
			var ip = getDeviceIp(player);
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ip, devicePort);
			clientSocket.send(sendPacket);
		} catch (Exception e) {
			System.out.println("Error sending command to client: " + e.getMessage());
		}
	}

	public void sendPlayerStateToClient(Player player, boolean gameRunning) {
		var bytes = MessageFromDevice.playerStateToBytes(player, gameRunning);
		sendBytesToClient(player, bytes);
	}

	protected void sendBytesToClient(Player player, byte[] bytes) {
		var ip = getDeviceIp(player);
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
			MessageFromDevice message = MessageFromDevice.fromBytes(packet.getData(), packet.getLength());
			var player = playerRegistry.getPlayerById(message.getPlayerId());
			if (getDeviceIp(player) == null || message.getHitByPlayerId() != 0) {//getHitByPlayerId cares the flag if device just stated
				setDeviceIp(player, packet.getAddress());
				System.out.printf("%s connected to player %d\n", this.getClass().getSimpleName(), player.getId());
				gameEventsListener.deviceConnected(player);
			}
			lastPingTime.set(message.getPlayerId(), System.currentTimeMillis());
			gameEventsListener.refreshConsoleTable();
			onMessageReceived(message);
			sendAckToClient(playerRegistry.getPlayerById(message.getPlayerId()));
		} catch (Exception e) {
			System.out.println("Error parsing message: " + e.getMessage());
		}
	}

	@Scheduled(fixedDelay = 1000)
	protected void sendHeartBeat() {
		var currentTime = System.currentTimeMillis();
		playerRegistry.getPlayers().forEach(player -> {
			var lastPing = lastPingTime.get(player.getId());
			if (currentTime - lastPing > pingTimeout) {
				if (getDeviceIp(player) != null) {
					System.out.printf("%s lost connection to player %d\n", this.getClass().getSimpleName(), player.getId());
					setDeviceIp(player, null);
					if (gameEventsListener != null) {
						gameEventsListener.refreshConsoleTable();
					}
				}
			}
		});
	}

}
