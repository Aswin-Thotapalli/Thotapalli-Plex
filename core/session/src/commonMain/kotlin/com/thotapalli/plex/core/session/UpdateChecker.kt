package com.thotapalli.plex.core.session

/**
 * The update check from CLAUDE.md section 17 points 3 and 4.
 *
 * A release publishes one static JSON manifest to a fixed URL alongside the artefacts. The
 * client fetches it at launch, at most once every twenty four hours, and compares the version
 * code for its own target against the running one. A newer code produces an
 * [UpdateCheckResult.Available] carrying the artefact URL, which the interface turns into a
 * single non-blocking notice with a download action.
 *
 * Nothing here touches Ktor. `core/api` is deliberately the only module that knows about an
 * HTTP client, so the fetch arrives as a [ManifestSource] the application wires up, exactly as
 * [ServerDirectory] takes its clock as `nowMs` rather than reading one itself. That also makes
 * every rule below testable with no network.
 */
class UpdateChecker(
    private val store: KeyValueStore,
    private val source: ManifestSource,
    private val target: UpdateTarget,
    private val currentVersionCode: Int,
    private val manifestUrl: String,
    private val nowMs: () -> Long,
    private val intervalMs: Long = CHECK_INTERVAL_MS,
) {

    /**
     * One check. Returns [UpdateCheckResult.Throttled] without touching the network when the
     * previous check was inside [intervalMs].
     *
     * Pass [force] for a user initiated check from Settings, which ignores the throttle.
     * This never throws: an update check must not be able to break a launch.
     */
    suspend fun check(force: Boolean = false): UpdateCheckResult {
        val now = nowMs()
        if (!force && !isDue(now)) return UpdateCheckResult.Throttled

        val body = try {
            source.fetch(manifestUrl)
        } catch (failure: Throwable) {
            // Deliberately no timestamp write. A failed fetch cost the user nothing, so the
            // next launch should retry rather than sit out a full day over one dropped request.
            return UpdateCheckResult.Unavailable(failure.message ?: "the manifest could not be fetched")
        }

        val manifest = try {
            parseUpdateManifest(body)
        } catch (malformed: ManifestFormatException) {
            return UpdateCheckResult.Unavailable(malformed.message ?: "the manifest is malformed")
        }

        // The timestamp lands only on a manifest that actually parsed, so a broken publish
        // does not suppress checking for the next twenty four hours.
        store.putString(KEY_LAST_CHECKED_AT, now.toString())

        val entry = manifest.entries[target]
            ?: return UpdateCheckResult.Unavailable("the manifest carries no ${target.manifestKey} artefact")

        return if (entry.versionCode > currentVersionCode) {
            UpdateCheckResult.Available(entry)
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    /** True when no check has run yet, or the last one is older than [intervalMs]. */
    fun isDue(now: Long = nowMs()): Boolean {
        val last = store.getString(KEY_LAST_CHECKED_AT)?.toLongOrNull() ?: return true
        // A device whose clock moved backwards would otherwise never check again, so treat
        // any negative elapsed time as due rather than clamping it to zero.
        val elapsed = now - last
        return elapsed < 0L || elapsed >= intervalMs
    }

    /** Drops the throttle. Used by sign-out, so a fresh install state checks on next launch. */
    fun forget() {
        store.remove(KEY_LAST_CHECKED_AT)
    }

    companion object {
        /** CLAUDE.md section 17 point 4: at most once per 24 hours. */
        const val CHECK_INTERVAL_MS: Long = 24L * 60L * 60L * 1000L

        internal const val KEY_LAST_CHECKED_AT = "update_last_checked_at"
    }
}

/**
 * Fetches the manifest body for a URL.
 *
 * A plain suspending call rather than a Ktor type, so `core/session` keeps no HTTP dependency
 * of its own. Each application module supplies one over whichever client it already holds.
 * An implementation is free to throw; [UpdateChecker] treats any failure as "no update known".
 */
fun interface ManifestSource {
    suspend fun fetch(url: String): String
}

/** The three artefacts a release publishes. The key is the field name in the manifest. */
enum class UpdateTarget(val manifestKey: String) {
    MOBILE("mobile"),
    TELEVISION("tv"),
    DESKTOP("desktop"),
}

/** One target's entry in the manifest. */
data class AvailableUpdate(
    val target: UpdateTarget,
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    /** Artefact size, shown beside the download action. Absent when the publisher omitted it. */
    val sizeBytes: Long? = null,
    /** Where the release notes live, if the publisher recorded a link. */
    val notesUrl: String? = null,
)

/** The parsed manifest: one entry per target that the release actually published. */
data class UpdateManifest(
    val schema: Int,
    val versionName: String,
    val entries: Map<UpdateTarget, AvailableUpdate>,
)

/** The outcome of one [UpdateChecker.check]. */
sealed interface UpdateCheckResult {
    /** A newer version code exists for this target. The only result that shows a notice. */
    data class Available(val update: AvailableUpdate) : UpdateCheckResult

    /** The manifest was read and this build is current. */
    data object UpToDate : UpdateCheckResult

    /** The previous check was inside the interval, so nothing was fetched. */
    data object Throttled : UpdateCheckResult

    /**
     * The manifest could not be fetched, parsed, or carried no entry for this target.
     * Never surfaced to the user; an update check that fails simply says nothing.
     */
    data class Unavailable(val reason: String) : UpdateCheckResult
}

/** The manifest asset name. Fixed, because the update URL must not change between releases. */
const val UPDATE_MANIFEST_FILE_NAME: String = "update-manifest.json"

/** The schema version this client understands. Bumped only on a breaking manifest change. */
const val UPDATE_MANIFEST_SCHEMA: Int = 1

/**
 * The fixed URL for [repository], given as `owner/name`.
 *
 * GitHub keeps `/releases/latest/download/<asset>` pointing at the newest published release,
 * which is what makes a static file on GitHub Releases satisfy the "fixed release URL" in
 * CLAUDE.md section 17 point 3 with no server of our own.
 */
fun githubLatestManifestUrl(repository: String): String =
    "https://github.com/$repository/releases/latest/download/$UPDATE_MANIFEST_FILE_NAME"

/** Raised for any manifest that is not readable. Never leaves [UpdateChecker.check]. */
class ManifestFormatException(message: String) : Exception(message)

/**
 * Reads a manifest body.
 *
 * Public so a test, or a future settings screen, can read a manifest without an
 * [UpdateChecker] around it.
 *
 * @throws ManifestFormatException on anything that is not a manifest of a schema we know.
 */
fun parseUpdateManifest(body: String): UpdateManifest {
    val root = JsonReader(body).parseDocument() as? JsonObjectValue
        ?: throw ManifestFormatException("the manifest root is not an object")

    val schema = root.entries["schema"].longOrNull()?.toInt()
        ?: throw ManifestFormatException("the manifest carries no schema")
    // Refusing a newer schema outright is safer than reading it optimistically: an old build
    // that guesses wrong would offer a download that does not apply to it.
    if (schema != UPDATE_MANIFEST_SCHEMA) {
        throw ManifestFormatException("unsupported manifest schema $schema")
    }

    val versionName = root.entries["versionName"].stringOrNull()
        ?: throw ManifestFormatException("the manifest carries no versionName")

    val targets = root.entries["targets"] as? JsonObjectValue
        ?: throw ManifestFormatException("the manifest carries no targets object")

    val entries = UpdateTarget.entries.mapNotNull { target ->
        val entry = targets.entries[target.manifestKey] as? JsonObjectValue ?: return@mapNotNull null
        target to target.readEntry(entry)
    }.toMap()

    return UpdateManifest(schema = schema, versionName = versionName, entries = entries)
}

private fun UpdateTarget.readEntry(entry: JsonObjectValue): AvailableUpdate {
    val versionCode = entry.entries["versionCode"].longOrNull()?.toInt()
        ?: throw ManifestFormatException("$manifestKey carries no versionCode")
    val versionName = entry.entries["versionName"].stringOrNull()
        ?: throw ManifestFormatException("$manifestKey carries no versionName")
    val url = entry.entries["url"].stringOrNull()
        ?: throw ManifestFormatException("$manifestKey carries no url")

    return AvailableUpdate(
        target = this,
        versionCode = versionCode,
        versionName = versionName,
        downloadUrl = url,
        sizeBytes = entry.entries["sizeBytes"].longOrNull(),
        notesUrl = entry.entries["notesUrl"].stringOrNull(),
    )
}

// --- A minimal JSON reader -------------------------------------------------------------------
// The manifest is a small first-party document this project publishes itself, and core/session
// has no serialisation dependency: core/api holds kotlinx.serialization as an implementation
// dependency precisely so response shapes stay inside that one module. Rather than widen that,
// the twenty lines of JSON the manifest actually uses are read here, the same way Uuid.kt writes
// out a UUID instead of reaching for a platform API.

private sealed interface JsonValue

private class JsonObjectValue(val entries: Map<String, JsonValue>) : JsonValue
private class JsonArrayValue(val items: List<JsonValue>) : JsonValue
private class JsonStringValue(val value: String) : JsonValue
private class JsonNumberValue(val literal: String) : JsonValue
private class JsonBooleanValue(val value: Boolean) : JsonValue
private data object JsonNullValue : JsonValue

private fun JsonValue?.stringOrNull(): String? = (this as? JsonStringValue)?.value

private fun JsonValue?.longOrNull(): Long? = when (this) {
    is JsonNumberValue -> literal.toLongOrNull()
        // A publisher that wrote 4.0 rather than 4 still means 4. A fractional value does not.
        ?: literal.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 }?.toLong()
    else -> null
}

private class JsonReader(private val text: String) {

    private var index = 0

    fun parseDocument(): JsonValue {
        val value = parseValue()
        skipWhitespace()
        if (index != text.length) fail("unexpected trailing content")
        return value
    }

    private fun parseValue(): JsonValue {
        skipWhitespace()
        return when (val c = peek()) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonStringValue(parseString())
            't' -> { expectWord("true"); JsonBooleanValue(true) }
            'f' -> { expectWord("false"); JsonBooleanValue(false) }
            'n' -> { expectWord("null"); JsonNullValue }
            else -> if (c == '-' || c in '0'..'9') parseNumber() else fail("unexpected character '$c'")
        }
    }

    private fun parseObject(): JsonObjectValue {
        expect('{')
        val entries = LinkedHashMap<String, JsonValue>()
        skipWhitespace()
        if (peek() == '}') {
            index++
            return JsonObjectValue(entries)
        }
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            entries[key] = parseValue()
            skipWhitespace()
            when (val c = take()) {
                ',' -> Unit
                '}' -> return JsonObjectValue(entries)
                else -> fail("expected ',' or '}' but found '$c'")
            }
        }
    }

    private fun parseArray(): JsonArrayValue {
        expect('[')
        val items = mutableListOf<JsonValue>()
        skipWhitespace()
        if (peek() == ']') {
            index++
            return JsonArrayValue(items)
        }
        while (true) {
            items += parseValue()
            skipWhitespace()
            when (val c = take()) {
                ',' -> Unit
                ']' -> return JsonArrayValue(items)
                else -> fail("expected ',' or ']' but found '$c'")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val builder = StringBuilder()
        while (true) {
            when (val c = take()) {
                '"' -> return builder.toString()
                '\\' -> builder.append(parseEscape())
                else -> {
                    if (c < ' ') fail("unescaped control character in a string")
                    builder.append(c)
                }
            }
        }
    }

    private fun parseEscape(): Char = when (val c = take()) {
        '"', '\\', '/' -> c
        'b' -> '\b'
        'f' -> '\u000C'
        'n' -> '\n'
        'r' -> '\r'
        't' -> '\t'
        'u' -> {
            if (index + 4 > text.length) fail("truncated unicode escape")
            val code = text.substring(index, index + 4).toIntOrNull(16)
                ?: fail("malformed unicode escape")
            index += 4
            code.toChar()
        }
        else -> fail("unknown escape '\\$c'")
    }

    private fun parseNumber(): JsonNumberValue {
        val start = index
        if (peek() == '-') index++
        while (index < text.length && (text[index] in '0'..'9' || text[index] in ".eE+-")) index++
        val literal = text.substring(start, index)
        if (literal.toDoubleOrNull() == null) fail("malformed number '$literal'")
        return JsonNumberValue(literal)
    }

    private fun skipWhitespace() {
        while (index < text.length && text[index] in " \t\r\n") index++
    }

    private fun peek(): Char {
        if (index >= text.length) fail("unexpected end of input")
        return text[index]
    }

    private fun take(): Char = peek().also { index++ }

    private fun expect(c: Char) {
        if (take() != c) fail("expected '$c'")
    }

    private fun expectWord(word: String) {
        if (!text.startsWith(word, index)) fail("expected '$word'")
        index += word.length
    }

    private fun fail(message: String): Nothing =
        throw ManifestFormatException("$message at offset $index")
}
