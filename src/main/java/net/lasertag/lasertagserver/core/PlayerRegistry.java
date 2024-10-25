package net.lasertag.lasertagserver.core;

import lombok.Getter;
import net.lasertag.lasertagserver.model.Player;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
@Getter
public class PlayerRegistry {

	private final List<Player> players = new ArrayList<>();

	public PlayerRegistry() {
		players.add(new Player(1, "Roma", 100));
		players.add(new Player(2, "Tima", 100));
		players.add(new Player(3, "Luka", 90));
		players.add(new Player(4, "Diana", 90));
	}

	public Player getPlayerById(int id) {
		return players.stream()
			.filter(player -> player.getId() == id)
			.findFirst()
			.orElse(null);
	}

	public Collection<Player> getOnlinePlayers() {
		return players.stream()
			.filter(Player::isOnline)
			.toList();
	}

	public List<Player> getPlayersSortedByScore() {
		return players.stream().sorted((p1, p2) -> p2.getScore() - p1.getScore()).toList();
	}

	public Player getLeadPlayer() {
		var maxScore = players.stream().mapToInt(Player::getScore).max().orElse(0);
		var leadPlayers = players.stream().filter(player -> player.getScore() == maxScore).toList();
		return leadPlayers.size() == 1 ? leadPlayers.get(0) : null;
	}

}
