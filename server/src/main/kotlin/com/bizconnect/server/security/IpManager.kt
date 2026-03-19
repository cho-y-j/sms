package com.bizconnect.server.security

import java.util.concurrent.ConcurrentHashMap

/**
 * IP address management for whitelist/blacklist and GeoIP blocking
 */
class IpManager {
    private val blacklistedIps = ConcurrentHashMap<String, IpBlacklistEntry>()
    private val whitelistedIps = ConcurrentHashMap<String, String>()

    /**
     * Check if IP is blacklisted
     */
    fun isIpBlacklisted(ipAddress: String): Boolean {
        val entry = blacklistedIps[ipAddress]
        if (entry != null) {
            // Check if blacklist has expired
            if (entry.expiresAt != null && System.currentTimeMillis() > entry.expiresAt) {
                blacklistedIps.remove(ipAddress)
                return false
            }
            return true
        }
        return false
    }

    /**
     * Add IP to blacklist
     */
    fun blacklistIp(
        ipAddress: String,
        reason: String,
        durationMs: Long? = null
    ) {
        val expiresAt = if (durationMs != null) {
            System.currentTimeMillis() + durationMs
        } else {
            null // Permanent blacklist
        }

        blacklistedIps[ipAddress] = IpBlacklistEntry(
            ipAddress = ipAddress,
            reason = reason,
            blockedAt = System.currentTimeMillis(),
            expiresAt = expiresAt
        )
    }

    /**
     * Remove IP from blacklist
     */
    fun unblacklistIp(ipAddress: String) {
        blacklistedIps.remove(ipAddress)
    }

    /**
     * Check if IP is whitelisted
     */
    fun isIpWhitelisted(ipAddress: String): Boolean {
        return whitelistedIps.containsKey(ipAddress)
    }

    /**
     * Add IP to whitelist
     */
    fun whitelistIp(ipAddress: String, description: String = "") {
        whitelistedIps[ipAddress] = description
    }

    /**
     * Check CIDR range
     */
    fun isIpInRange(ipAddress: String, cidrRange: String): Boolean {
        return try {
            val parts = cidrRange.split("/")
            if (parts.size != 2) return false

            val baseIp = parts[0]
            val prefixLength = parts[1].toInt()

            val ipParts = ipAddress.split(".").map { it.toInt() }
            val baseParts = baseIp.split(".").map { it.toInt() }

            if (ipParts.size != 4 || baseParts.size != 4) return false

            var ipInt = 0
            var baseInt = 0
            for (i in 0..3) {
                ipInt = (ipInt shl 8) or ipParts[i]
                baseInt = (baseInt shl 8) or baseParts[i]
            }

            val mask = if (prefixLength == 0) 0 else (-1 shl (32 - prefixLength))
            (ipInt and mask) == (baseInt and mask)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get blacklist entries for admin view
     */
    fun getBlacklistedIps(): List<IpBlacklistEntry> {
        val now = System.currentTimeMillis()
        // Remove expired entries
        blacklistedIps.filterValues { entry ->
            entry.expiresAt != null && entry.expiresAt < now
        }.keys.forEach { blacklistedIps.remove(it) }

        return blacklistedIps.values.toList()
    }

    /**
     * Clear expired entries
     */
    fun cleanupExpiredEntries() {
        val now = System.currentTimeMillis()
        blacklistedIps.filterValues { entry ->
            entry.expiresAt != null && entry.expiresAt < now
        }.keys.forEach { blacklistedIps.remove(it) }
    }
}

data class IpBlacklistEntry(
    val ipAddress: String,
    val reason: String,
    val blockedAt: Long,
    val expiresAt: Long? = null
)
