/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.test

import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(DockerExtension::class, SharedSpringContext::class)
open class IntegrationTest