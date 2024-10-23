package net.lasertag.lasertagserver.core;

import jakarta.annotation.PostConstruct;
import net.lasertag.lasertagserver.model.MessageFromDevice;
import net.lasertag.lasertagserver.model.Player;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.concurrent.Executor;

@Component
public class GunCommunication extends AbstractUdpServer {

	private final Executor daemonExecutor;

	public GunCommunication(PlayerRegistry playerRegistry,
							Executor daemonExecutor) {
		super(9876, 9876, playerRegistry);
		this.daemonExecutor = daemonExecutor;
	}

	@PostConstruct
	public void init() {
		daemonExecutor.execute(this::startUdpServer);
		Runtime.getRuntime().addShutdownHook(new Thread(this::stopUdpServer));
	}

	@Override
	protected InetAddress getDeviceIp(Player player) {
		return player.getGunIp();
	}

	@Override
	protected void setDeviceIp(Player player, InetAddress ip) {
		player.setGunIp(ip);
	}

	@Override
	protected void onMessageReceived(MessageFromDevice message) {
		var player = playerRegistry.getPlayerById(message.getPlayerId());
		switch (message.getType()) {
			case MessageFromDevice.TYPE_GUN_SHOT -> gameEventsListener.eventGunShot(player);
			case MessageFromDevice.TYPE_GUN_RELOAD -> gameEventsListener.eventGunReload(player);
		}
	}


}
