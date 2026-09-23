package com.englishpod.learning.core

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Validates every outbound server address the app is about to contact.
 *
 * Rules enforced here:
 *  - only `http` and `https` are allowed;
 *  - loopback, private, link-local and reserved hosts/addresses are rejected.
 *
 * `parse` performs the synchronous checks on the URL itself; [ensurePublicHost] additionally
 * resolves the host name and rejects it when it points at a blocked address.
 */
object UrlGuard {

    class Rejected(message: String) : IllegalArgumentException(message)

    private val blockedNames = setOf(
        "localhost",
        "localhost.localdomain",
        "ip6-localhost",
        "ip6-loopback",
        "broadcasthost",
        "metadata",
        "metadata.google.internal",
        "instance-data",
    )

    private val blockedSuffixes = listOf(
        ".localhost",
        ".local",
        ".localdomain",
        ".internal",
        ".home.arpa",
        ".in-addr.arpa",
        ".ip6.arpa",
    )

    /** Hosts already proven to resolve to public addresses only. */
    private val publicHosts = ConcurrentHashMap<String, Boolean>()

    fun parse(raw: String): HttpUrl {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw Rejected("服务器地址不能为空")
        if (trimmed.contains("://") && !trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw Rejected("只允许 http / https 协议")
        }
        val url = trimmed.toHttpUrlOrNull() ?: throw Rejected("地址格式不正确：$trimmed")
        if (url.scheme != "http" && url.scheme != "https") {
            throw Rejected("只允许 http / https 协议，当前为 ${url.scheme}")
        }
        val host = url.host.lowercase().trimEnd('.')
        if (host.isEmpty()) throw Rejected("地址缺少主机名")
        if (host in blockedNames || blockedSuffixes.any { host.endsWith(it) }) {
            throw Rejected("已拒绝本机 / 内网 / 保留地址：$host")
        }
        if (isIpLiteral(host) && isBlockedIp(host)) {
            throw Rejected("已拒绝本机 / 内网 / 保留地址：$host")
        }
        return url
    }

    /**
     * Resolves [url]'s host and rejects the request when any resolved address is loopback,
     * private, link-local or reserved. Results are cached per host for the process lifetime.
     */
    suspend fun ensurePublicHost(url: HttpUrl) {
        val host = url.host.lowercase()
        if (isIpLiteral(host)) return
        if (publicHosts[host] == true) return
        val addresses = withContext(Dispatchers.IO) {
            try {
                InetAddress.getAllByName(host)?.toList().orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
        }
        val clean = addresses.isNotEmpty() && addresses.none { isBlockedIp(it.hostAddress.orEmpty()) }
        if (!clean) throw Rejected("已拒绝本机 / 内网 / 保留地址：$host")
        publicHosts[host] = true
    }

    fun isIpLiteral(host: String): Boolean =
        host.contains(':') || (host.isNotEmpty() && host.all { it.isDigit() || it == '.' })

    fun isBlockedIp(address: String): Boolean {
        val text = address.substringBefore('%').trim()
        if (text.isEmpty()) return true
        return if (text.contains(':')) isBlockedIpv6(text) else isBlockedIpv4(text)
    }

    private fun parseIpv4(text: String): IntArray? {
        val parts = text.split('.')
        if (parts.size != 4) return null
        val out = IntArray(4)
        for (i in 0..3) {
            val part = parts[i]
            if (part.isEmpty() || part.length > 3 || !part.all { it.isDigit() }) return null
            val value = part.toInt()
            if (value > 255) return null
            out[i] = value
        }
        return out
    }

    private fun isBlockedIpv4(text: String): Boolean {
        val octets = parseIpv4(text) ?: return false
        val (a, b, c, _) = octets
        return when {
            a == 0 -> true                     // 0.0.0.0/8 "this network"
            a == 10 -> true                    // 10.0.0.0/8 private
            a == 127 -> true                   // 127.0.0.0/8 loopback
            a == 100 && b in 64..127 -> true    // 100.64.0.0/10 carrier grade NAT
            a == 169 && b == 254 -> true        // 169.254.0.0/16 link-local
            a == 172 && b in 16..31 -> true     // 172.16.0.0/12 private
            a == 192 && b == 0 && c == 0 -> true
            a == 192 && b == 0 && c == 2 -> true
            a == 192 && b == 88 && c == 99 -> true
            a == 192 && b == 168 -> true        // 192.168.0.0/16 private
            a == 198 && b in 18..19 -> true     // 198.18.0.0/15 benchmarking
            a == 198 && b == 51 && c == 100 -> true
            a == 203 && b == 0 && c == 113 -> true
            a >= 224 -> true                    // multicast + reserved + broadcast
            else -> false
        }
    }

    private fun expandIpv6(text: String): IntArray? {
        val trimmed = text.substringBefore('%')
        if (trimmed.count { it == ':' } < 2 && !trimmed.contains("::")) return null
        val halves = trimmed.split("::")
        if (halves.size > 2) return null

        fun groups(segment: String): List<Int>? {
            if (segment.isEmpty()) return emptyList()
            val out = ArrayList<Int>()
            for (group in segment.split(':')) {
                if (group.isEmpty()) return null
                if (group.contains('.')) {
                    val v4 = parseIpv4(group) ?: return null
                    out.add((v4[0] shl 8) or v4[1])
                    out.add((v4[2] shl 8) or v4[3])
                } else {
                    if (group.length > 4) return null
                    out.add(group.toIntOrNull(16) ?: return null)
                }
            }
            return out
        }

        val head = groups(halves[0]) ?: return null
        val tail = if (halves.size == 2) groups(halves[1]) ?: return null else emptyList()
        if (head.size + tail.size > 8) return null
        val all = IntArray(8)
        for (i in head.indices) all[i] = head[i]
        for (i in tail.indices) all[8 - tail.size + i] = tail[i]
        return all
    }

    private fun fromHextets(high: Int, low: Int): String =
        "${high shr 8}.${high and 0xFF}.${low shr 8}.${low and 0xFF}"

    private fun isBlockedIpv6(text: String): Boolean {
        val g = expandIpv6(text) ?: return false
        if (g.all { it == 0 }) return true                                        // ::
        if ((g[0] or g[1] or g[2] or g[3] or g[4] or g[5] or g[6]) == 0 && g[7] == 1) return true // ::1
        if (g[0] == 0 && g[1] == 0 && g[2] == 0 && g[3] == 0 && g[4] == 0 && g[5] == 0xFFFF) {
            return isBlockedIpv4(fromHextets(g[6], g[7]))                          // ::ffff:a.b.c.d
        }
        if (g[0] == 0x64 && g[1] == 0xFF9B && g[2] == 0 && g[3] == 0 && g[4] == 0 && g[5] == 0) {
            return isBlockedIpv4(fromHextets(g[6], g[7]))                          // 64:ff9b::/96 NAT64
        }
        if (g[0] == 0x2002) return isBlockedIpv4(fromHextets(g[1], g[2]))          // 6to4 embeds IPv4
        if ((g[0] and 0xFE00) == 0xFC00) return true                              // fc00::/7 unique local
        if ((g[0] and 0xFFC0) == 0xFE80) return true                              // fe80::/10 link-local
        if ((g[0] and 0xFF00) == 0xFF00) return true                              // ff00::/8 multicast
        if (g[0] == 0x2001 && g[1] == 0x0DB8) return true                         // 2001:db8::/32 docs
        if (g[0] == 0x2001 && g[1] == 0x0000) return true                         // 2001::/32 Teredo
        if (g[0] == 0x0064 && g[1] == 0xFF9B) return true                         // other NAT64 forms
        return false
    }
}
