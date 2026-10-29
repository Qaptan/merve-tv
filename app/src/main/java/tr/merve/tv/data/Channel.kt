package tr.merve.tv.data

import kotlinx.serialization.Serializable

@Serializable
data class ChannelList(
    val version: Int = 1,
    val generatedAt: String = "",
    val groups: List<Group> = emptyList(),
    val channels: List<Channel> = emptyList(),
)

@Serializable
data class Group(val id: String, val name: String)

@Serializable
data class Channel(
    val id: String,
    val name: String,
    val logo: String? = null,
    val group: String,
    val categories: List<String> = emptyList(),
    val alive: Boolean = true,
    val streams: List<Stream> = emptyList(),
) {
    val initials: String
        get() = name.split(' ', '-').filter { it.isNotBlank() }.take(2)
            .joinToString(" ") { if (it.length <= 4) it else it.take(1) }
            .uppercase()
}

@Serializable
data class Stream(
    val url: String,
    val quality: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val labels: List<String> = emptyList(),
    val ok: Boolean = true,
)
