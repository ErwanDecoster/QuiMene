package com.quimene.designsystem.components

/** Miroir de `Avatar.Kind` (`Avatar.swift`). */
sealed interface AvatarKind {
    data class Symbol(
        val name: String,
    ) : AvatarKind

    data class Emoji(
        val character: String,
    ) : AvatarKind

    class Photo(
        val data: ByteArray,
    ) : AvatarKind {
        override fun equals(other: Any?): Boolean = other is Photo && data.contentEquals(other.data)

        override fun hashCode(): Int = data.contentHashCode()
    }
}

/** Doc 08 « Avatars » — trois sources, toutes hors ligne. Miroir de `Avatar.swift`. */
data class Avatar(
    val kind: AvatarKind,
    val palette: PlayerPalette,
) {
    companion object {
        /** Sélection curatée d'emoji (animaux, objets, expressions) — seule source d'avatar par
         * défaut : pas de symbole à choisir, l'emoji vient du hachage du pseudo. Liste identique
         * à `Avatar.curatedEmoji` (même ordre — l'index dans cette liste fait partie du calcul
         * déterministe de [generated]). */
        val curatedEmoji: List<String> =
            listOf(
                "🦊",
                "🐻",
                "🐼",
                "🐨",
                "🐯",
                "🦁",
                "🐮",
                "🐷",
                "🐸",
                "🐵",
                "🐔",
                "🐧",
                "🐦",
                "🦆",
                "🦉",
                "🦇",
                "🐺",
                "🐗",
                "🐴",
                "🦄",
                "🐝",
                "🦋",
                "🐢",
                "🐍",
                "🦎",
                "🐙",
                "🦑",
                "🦀",
                "🐠",
                "🐬",
                "🐳",
                "🦈",
                "🐌",
                "🐞",
                "🦔",
                "🦥",
                "🦦",
                "🦨",
                "🦡",
                "🎨",
                "🎮",
                "🎸",
                "🚀",
                "⚽️",
                "🏀",
                "🎯",
                "🎲",
                "🍕",
                "🌈",
                "⭐️",
                "🔥",
                "💎",
                "🍀",
                "🎈",
                "🤖",
                "👾",
                "🧙",
                "🥷",
                "🏎️",
                "🌸",
                "🦝",
                "🐲",
                "🍄",
                "⚡",
                "♥️",
                "💚",
                "💙",
                "🩷",
            )

        /**
         * Avatar déterministe dérivé du pseudo : même pseudo → même avatar, sur les deux
         * plateformes. `String.hashCode()` de la JVM n'est PAS salé aléatoirement (contrairement
         * à `String.hashValue` en Swift) mais diverge de toute façon de l'algorithme Swift —
         * d'où le FNV-1a manuel ci-dessous, bit-exact avec `Avatar.fnv1a` (mêmes constantes,
         * même parcours octet-par-octet UTF-8, même arithmétique 64 bits non signée avec
         * dépassement silencieux).
         */
        fun generated(nickname: String): Avatar {
            val hash = fnv1a(nickname)
            val paletteIndex = (hash % 10uL).toInt() + 1
            val emojiIndex = ((hash / 10uL) % curatedEmoji.size.toULong()).toInt()
            return Avatar(
                kind = AvatarKind.Emoji(curatedEmoji[emojiIndex]),
                palette = PlayerPalette(paletteIndex),
            )
        }

        private fun fnv1a(string: String): ULong {
            var hash = 0xcbf29ce484222325uL
            for (byte in string.toByteArray(Charsets.UTF_8)) {
                hash = hash xor byte.toUByte().toULong()
                hash *= 0x100000001b3uL
            }
            return hash
        }
    }
}
