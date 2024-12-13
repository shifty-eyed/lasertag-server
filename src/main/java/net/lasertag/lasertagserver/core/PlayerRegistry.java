package net.lasertag.lasertagserver.core;

import lombok.Getter;
import net.lasertag.lasertagserver.model.Player;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Getter
public class PlayerRegistry {

	private final List<Player> players = new ArrayList<>();

	public PlayerRegistry() {
		players.add(new Player(1, "Roma", 100));
		players.add(new Player(2, "Tima", 100));
		players.add(new Player(3, "Luka", 100));
		players.add(new Player(4, "Diana", 100));
		players.add(new Player(5, "Player5", 100));
		players.add(new Player(6, "Player6", 100));

	}

	public LinkedHashMap<Integer, Integer> getTeamScores() {
		// teamId -> score, sorted by highest score
		return players.stream()
			.collect(Collectors.groupingBy(Player::getTeamId, Collectors.summingInt(Player::getScore)))
			.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
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

	public int getLeadTeam() {
		var teamScores = getTeamScores();
		var maxScore = teamScores.values().stream().max(Integer::compareTo).orElse(0);
		var leadTeams = teamScores.entrySet().stream()
			.filter(entry -> Objects.equals(entry.getValue(), maxScore))
			.map(Map.Entry::getKey)
			.toList();
		return leadTeams.size() == 1 ? leadTeams.get(0) : -1;
	}

}
