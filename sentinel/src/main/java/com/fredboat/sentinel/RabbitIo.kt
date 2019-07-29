package com.fredboat.sentinel

import com.fredboat.sentinel.SentinelExchanges.FANOUT
import com.fredboat.sentinel.SentinelExchanges.REQUESTS
import com.fredboat.sentinel.SentinelExchanges.SESSIONS
import com.fredboat.sentinel.config.RoutingKey
import com.fredboat.sentinel.jda.RemoteSessionController
import com.fredboat.sentinel.jda.SetGlobalRatelimit
import com.fredboat.sentinel.rpc.meta.FanoutRequest
import com.fredboat.sentinel.rpc.meta.ReactiveConsumer
import com.fredboat.sentinel.rpc.meta.SentinelRequest
import com.fredboat.sentinel.util.Rabbit
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.rabbitmq.*

@Controller
class RabbitIo(
        private val sender: Sender,
        private val receiver: Receiver,
        private val rabbit: Rabbit,
        private val routingKey: RoutingKey,
        private val sessionControl: RemoteSessionController
): ApplicationContextAware {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(RabbitIo::class.java)
    }

    private val sessionQueueName = "sessions-${routingKey.key}"
    private val fanoutQueueName = "fanout-${routingKey.key}"

    override fun setApplicationContext(spring: ApplicationContext) {
        Flux.concat(declareExchanges())
                .count()
                .doOnSuccess { log.info("Declared $it exchanges") }
                .thenMany(Flux.concat(declareQueues()))
                .count()
                .doOnSuccess { log.info("Declared $it queues") }
                .thenMany(Flux.concat(declareBindings()))
                .count()
                .doOnSuccess { log.info("Declared $it bindings") }
                .subscribe { configureReceiver(spring) }
    }

    private fun configureReceiver(spring: ApplicationContext) {
        receiver.consumeAutoAck(SESSIONS).subscribe {
            val event = rabbit.fromJson(it, SetGlobalRatelimit::class.java)
            sessionControl.handleRatelimitSet(event)
        }

        val requestsHandler = ReactiveConsumer(rabbit, spring, SentinelRequest::class.java)
        receiver.consumeAutoAck(REQUESTS).subscribe { requestsHandler.handleIncoming(it) }
        val fanoutHandler = ReactiveConsumer(rabbit, spring, FanoutRequest::class.java)
        receiver.consumeAutoAck(FANOUT).subscribe { fanoutHandler.handleIncoming(it) }
    }

    private fun declareExchanges() = mutableListOf(
            declareExchange(REQUESTS),
            declareExchange(SESSIONS),
            declareExchange(FANOUT, type = "fanout")
    )

    private fun declareQueues() = mutableListOf(
            declareQueue(REQUESTS),
            declareQueue(sessionQueueName),
            declareQueue(fanoutQueueName)
    )

    private fun declareBindings() = mutableListOf(
            declareBinding(REQUESTS, REQUESTS, routingKey),
            declareBinding(SESSIONS, sessionQueueName),
            declareBinding(FANOUT, fanoutQueueName)
    )

    private fun declareExchange(
            name: String,
            type: String = "direct"
    ) = sender.declareExchange(ExchangeSpecification().apply {
        name(name)
        durable(false)
        autoDelete(true)
        type(type)
    })

    private fun declareQueue(name: String) = sender.declareQueue(QueueSpecification().apply {
        name(name)
        durable(false)
        autoDelete(true)
        exclusive(false)
    })

    private fun declareBinding(
            exchange: String,
            queue: String,
            key: RoutingKey? = null
    ) = sender.bind(BindingSpecification().apply {
        exchange(exchange)
        queue(queue)
        routingKey(key?.key ?: "")
    })
}