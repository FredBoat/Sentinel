package com.fredboat.sentinel.rpc

import com.fredboat.sentinel.entities.Guild
import com.fredboat.sentinel.entities.GuildSubscribeRequest
import com.fredboat.sentinel.entities.GuildUnsubscribeRequest
import com.fredboat.sentinel.extension.toEntity
import com.fredboat.sentinel.jda.VoiceServerUpdateCache
import net.dv8tion.jda.bot.sharding.ShardManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class SubscriptionHandler(
        @param:Qualifier("guildSubscriptions")
        private val subscriptions: MutableSet<Long>,
        private val shardManager: ShardManager,
        private val voiceServerUpdateCache: VoiceServerUpdateCache
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SubscriptionHandler::class.java)
    }

    fun consume(request: GuildSubscribeRequest): Guild? {
        val guild = shardManager.getGuildById(request.id)

        if (guild == null) {
            log.warn("Attempt to subscribe to unknown guild ${request.id}")
            return null
        }

        val added = subscriptions.add(request.id)
        if (!added) {
            if (subscriptions.contains(request.id)) {
                log.warn("Attempt to subscribe ${request.id} while we are already subscribed")
            } else {
                log.error("Failed to subscribe to ${request.id}")
            }
        }

        val entity = guild.toEntity(voiceServerUpdateCache)
        log.info("Subscribed to ${request.id}")
        return entity
    }

    fun consume(request: GuildUnsubscribeRequest) {
        val removed = subscriptions.remove(request.id)
        if (!removed) {
            if (!subscriptions.contains(request.id)) {
                log.warn("Attempt to unsubscribe ${request.id} while we are not subscribed")
            } else {
                log.error("Failed to unsubscribe from ${request.id}")
            }
        }
    }
}