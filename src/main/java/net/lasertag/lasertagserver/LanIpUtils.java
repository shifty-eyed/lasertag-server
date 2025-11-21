package net.lasertag.lasertagserver;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility method for discovering the preferred LAN IP address of the current host.
 */
public final class LanIpUtils {

	private LanIpUtils() {
		// utility class
	}

	/**
	 * Find the preferred LAN IPv4 address as a string.
	 * <p>
	 * Preference order:
	 * <ol>
	 *   <li>First non-loopback IPv4 address starting with {@code 192.168.}</li>
	 *   <li>Otherwise, first non-loopback IPv4 site-local address (10.x, 172.16–31.x, 192.168.x)</li>
	 * </ol>
	 *
	 * @return optional string representation of the preferred LAN IP
	 */
	public static Optional<String> findLanIp() {
		List<InetAddress> candidates = findCandidateIpv4Addresses();

		// Prefer 192.168.x.x
		for (InetAddress addr : candidates) {
			if (addr.getHostAddress().startsWith("192.168.")) {
				return Optional.of(addr.getHostAddress());
			}
		}

		// Fallback to first site-local candidate (list is already filtered)
		if (!candidates.isEmpty()) {
			return Optional.of(candidates.get(0).getHostAddress());
		}

		return Optional.empty();
	}

	private static List<InetAddress> findCandidateIpv4Addresses() {
		List<InetAddress> result = new ArrayList<>();

		Enumeration<NetworkInterface> interfaces;
		try {
			interfaces = NetworkInterface.getNetworkInterfaces();
		}
		catch (SocketException e) {
			return Collections.emptyList();
		}

		if (interfaces == null) {
			return Collections.emptyList();
		}

		while (interfaces.hasMoreElements()) {
			NetworkInterface nif = interfaces.nextElement();
			try {
				if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) {
					continue;
				}
			}
			catch (SocketException e) {
				continue;
			}

			Enumeration<InetAddress> addresses = nif.getInetAddresses();
			while (addresses.hasMoreElements()) {
				InetAddress addr = addresses.nextElement();
				if (addr instanceof Inet4Address
					&& !addr.isLoopbackAddress()
					&& addr.isSiteLocalAddress()) {
					result.add(addr);
				}
			}
		}

		return result;
	}
}


