package teikengstudio.xmpro.api

import org.json.JSONArray
import org.json.JSONObject
import teikengstudio.xmpro.model.Credentials

/** long: 账号接口仅记录排障需要的业务结果和字段结构，避免登录凭据随日志导出。 */
internal object AccountRequestDiagnostics {
    fun accepts(path: String): Boolean = path.startsWith("/lite-earn/ting/welfare/") ||
        path.startsWith("/revision/follow/")

    fun event(
        id: Long,
        method: String,
        path: String,
        body: String?,
        credentials: Credentials?,
        status: Int? = null,
        response: String? = null,
        elapsedMs: Long? = null,
        error: String? = null,
    ): String = JSONObject().apply {
        put("time", System.currentTimeMillis())
        put("request", id)
        put("method", method)
        put("endpoint", path.substringBefore("/ts-"))
        put("phase", if (status == null && error == null) "request" else "result")
        put("authenticated", credentials != null)
        val fields = runCatching { JSONObject(body.orEmpty()) }.getOrNull()
        put("fields", JSONArray(fields?.keys()?.asSequence()?.sorted()?.toList() ?: emptyList<String>()))
        status?.let { put("http", it) }
        elapsedMs?.let { put("elapsed_ms", it) }
        error?.let { put("error_type", it.take(80)) }
        val payload = runCatching { JSONObject(response.orEmpty()) }.getOrNull()
        if (response != null) put("json", payload != null)
        fun copyResult(from: JSONObject, to: JSONObject) {
            for (key in listOf("ret", "success", "code", "errorCode", "msg", "message", "toast")) {
                val value = from.opt(key) ?: continue
                if (value is Number || value is Boolean) to.put(key, value)
                if (value is String) to.put(key, redact(value, credentials))
            }
        }
        payload?.let { copyResult(it, this) }
        payload?.optJSONObject("data")?.let { data ->
            put("data", JSONObject().apply {
                copyResult(data, this)
                for (key in listOf("rewardDuration", "availableListenDuration")) {
                    (data.opt(key) as? Number)?.let { put(key, it) }
                }
                if (data.has("nextTier")) put("next_tier_present", !data.isNull("nextTier"))
                data.optJSONArray("tierList")?.let { tiers ->
                    put("tiers", JSONArray().apply {
                        for (i in 0 until tiers.length()) {
                            val tier = tiers.optJSONObject(i) ?: continue
                            put(JSONObject().apply {
                                for (key in listOf("index", "tierDuration", "status", "receiveTime")) {
                                    (tier.opt(key) as? Number)?.let { put(key, it) }
                                }
                            })
                        }
                    })
                }
            })
        }
    }.toString()

    private fun redact(value: String, credentials: Credentials?): String {
        var text = value
        credentials?.let { account ->
            for (secret in listOf(account.token, account.token.substringAfter('&'), account.deviceId, account.uid)) {
                if (secret.isNotEmpty()) text = text.replace(secret, "[redacted]")
            }
        }
        return text.replace(Regex("https?://[^\\s]+"), "[url]")
            .replace(Regex("[A-Za-z0-9_+/=.&-]{24,}"), "[redacted]")
            .replace(Regex("[\\r\\n\\t]"), " ").take(160)
    }
}
