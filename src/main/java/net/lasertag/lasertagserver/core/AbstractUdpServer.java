package net.lasertag.lasertagserver.core;

import net.lasertag.lasertagserver.model.MessageFromDevice;
import net.lasertag.lasertagserver.model.Player;
import org.springframework.scheduling.annotation.Scheduled;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractUdpServer {

	private static final int CLIENT_PORT = 1234;

	protected final PlayerRegistry playerRegistry;
	private final List<Long> lastPingTime;

	private volatile boolean running = true;
	private final int port;

	public AbstractUdpServer(int port, PlayerRegistry playerRegistry) {
		this.port = port;
		this.playerRegistry = playerRegistry;

		this.lastPingTime = new ArrayList<>(playerRegistry.getPlayers().size());
		for (int i = 0; i < playerRegistry.getPlayers().size(); i++) {
			lastPingTime.add(0L);
		}
	}

	protected abstract InetAddress getDeviceIp(Player player);
	protected abstract void setDeviceIp(Player player, InetAddress ip);
	protected abstract void onMessageReceived(MessageFromDevice message);



	public void startUdpServer() {
		try (DatagramSocket serverSocket = new DatagramSocket(port)) {
			System.out.printf("UDP Server: %s started on port: %d thread: %s",
				this.getClass().getSimpleName(), port, Thread.currentThread().getName());

			serverSocket.setSoTimeout(500);

			byte[] receiveBuffer = new byte[256];

			while (running) {
				DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
				try {
					serverSocket.receive(receivePacket);
				} catch (SocketTimeoutException e) {
					continue;
				}
				processPacketFromClient(receivePacket);
				Thread.yield();
			}
			System.out.println("UDP Server stopped");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void sendAckToClient(Player player) {
		try (DatagramSocket clientSocket = new DatagramSocket()) {
			byte[] sendData = new byte[] { 1 };
			var ip = getDeviceIp(player);
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, getDeviceIp(player), CLIENT_PORT);
			clientSocket.send(sendPacket);
			System.out.println("Command ACK to client: " + ip.getHostAddress());
		} catch (Exception e) {
			System.out.println("Error sending command to client: " + e.getMessage());
		}
	}

	protected void sendBytesToClient(Player player, byte[] bytes) {
		var ip = getDeviceIp(player);
		try (DatagramSocket clientSocket = new DatagramSocket()) {
			DatagramPacket sendPacket = new DatagramPacket(bytes, bytes.length, ip, CLIENT_PORT);
			clientSocket.send(sendPacket);
			System.out.println("Command to client: " + ip.getHostAddress());
		} catch (Exception e) {
			System.out.println("Error sending command to client: " + e.getMessage());
		}
	}

	public void stopUdpServer() {
		running = false;
		System.out.println("Stopping UDP Server...");
	}

	private void processPacketFromClient(DatagramPacket packet) {
		try {
			MessageFromDevice message = MessageFromDevice.fromBytes(packet.getData());
			setDeviceIp(playerRegistry.getPlayerById(message.getPlayerId()), packet.getAddress());
			lastPingTime.set(message.getPlayerId(), System.currentTimeMillis());
			onMessageReceived(message);
			sendAckToClient(playerRegistry.getPlayerById(message.getPlayerId()));
		} catch (Exception e) {
			System.out.println("Error parsing message: " + e.getMessage());
		}
	}

	@Scheduled(fixedRate = 1000)
	protected void sendHeartBeat() {
		var currentTime = System.currentTimeMillis();
		playerRegistry.getOnlinePlayers().forEach(player -> {
			var lastPing = lastPingTime.get(player.getId());
			if (currentTime - lastPing > 5000) {
				setDeviceIp(player, null);
			}
		});
	}

}
