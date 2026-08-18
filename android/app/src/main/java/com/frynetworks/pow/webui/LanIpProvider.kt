package com.frynetworks.pow.webui

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Best-guess LAN IPv4 without ACCESS_WIFI_STATE: NetworkInterface needs no permission
 * and also covers Ethernet-only TV boxes. Site-local addresses win, then real NICs
 * (wlan/eth) beat tunnels so a VPN's 100.x address never masks the LAN one.
 */
object LanIpProvider {

    fun lanIp(): String? {
        val candidates = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
                .flatMap { nif ->
                    nif.inetAddresses.toList()
                        .filterIsInstance<Inet4Address>()
                        .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                        .map { nif.name.lowercase() to it }
                }
        } catch (e: Exception) {
            return null
        }
        return candidates
            .sortedWith(
                compareBy(
                    { !it.second.isSiteLocalAddress },
                    { !(it.first.startsWith("wlan") || it.first.startsWith("eth")) },
                ),
            )
            .firstOrNull()?.second?.hostAddress
    }
}
