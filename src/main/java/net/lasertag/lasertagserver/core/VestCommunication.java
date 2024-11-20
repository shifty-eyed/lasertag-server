	package net.lasertag.lasertagserver.core;

import jakarta.annotation.PostConstruct;
import lombok.Setter;
import net.lasertag.lasertagserver.model.MessageFromDevice;
import net.lasertag.lasertagserver.model.Player;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.concurrent.Executor;

@Component
public class VestCommunication extends AbstractUdpServer {

	public VestCommunication(PlayerRegistry playerRegistry,
							 ThreadPoolTaskExecutor daemonExecutor) {
		super(9877, 1234, playerRegistry, daemonExecutor);
	}

	@PostConstruct
	public void init() {
		daemonExecutor.execute(this::startUdpServer);
		Runtime.getRuntime().addShutdownHook(new Thread(this::stopUdpServer));
	}

	@Override
	protected InetAddress getDeviceIp(Player player) {
		return player.getVestIp();
	}

	@Override
	protected void setDeviceIp(Player player, InetAddress ip) {
		player.setVestIp(ip);
	}

	@Override
	protected void onMessageReceived(MessageFromDevice message) {
		var player = playerRegistry.getPlayerById(message.getPlayerId());
		var hitByPlayer = playerRegistry.getPlayerById(message.getHitByPlayerId());
		switch (message.getType()) {
			case MessageFromDevice.TYPE_VEST_HIT -> gameEventsListener.eventVestGotHit(player, hitByPlayer);
		}
	}

}
