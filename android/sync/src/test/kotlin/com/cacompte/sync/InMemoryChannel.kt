package com.cacompte.sync

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Doc 09 « Tests », miroir de `InMemoryTransport.swift` — une paire de [TransportSession] en
 * mémoire, pour vérifier la convergence de [LiveSession] sans dépendre d'un vrai transport
 * (Supabase Realtime). Ce qu'on envoie sur l'un ressort sur `incoming` de l'autre.
 */
class InMemoryChannel private constructor(
    private val incomingChannel: Channel<ByteArray>,
    private val outgoingChannel: Channel<ByteArray>,
) : TransportSession {
    override val incoming: Flow<ByteArray> = incomingChannel.receiveAsFlow()

    override suspend fun send(data: ByteArray) {
        outgoingChannel.send(data)
    }

    override suspend fun close() {
        outgoingChannel.close()
    }

    companion object {
        fun pair(): Pair<InMemoryChannel, InMemoryChannel> {
            val channelA = Channel<ByteArray>(Channel.UNLIMITED)
            val channelB = Channel<ByteArray>(Channel.UNLIMITED)
            val endA = InMemoryChannel(incomingChannel = channelA, outgoingChannel = channelB)
            val endB = InMemoryChannel(incomingChannel = channelB, outgoingChannel = channelA)
            return endA to endB
        }
    }
}
