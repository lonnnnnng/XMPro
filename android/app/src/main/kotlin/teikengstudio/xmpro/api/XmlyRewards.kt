package teikengstudio.xmpro.api

import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject
import teikengstudio.xmpro.model.Quota
import teikengstudio.xmpro.model.XmlyException

/** One welfare tier: how much it grants and when it was last granted. */
data class RewardTier(
    val index: Int,
    val seconds: Int,
    val label: String,
    val status: Int,
    val receiveTime: Long,
)

/**
 * Listening-time welfare: query the task list and claim more minutes.
 *
 * Every claim POST is single-shot; an unknown outcome must be checked in the
 * official app rather than replayed, otherwise minutes could be double counted.
 */
class XmlyRewards(
    private val client: XmlyClient,
    private val clock: () -> Long = { ServerClock.now() },
) {
    @Volatile
    var lastError: String = ""
        private set

    var shouldPause: Boolean = false
        private set

    private fun uid(): String {
        val account = client.credentials
            ?: throw XmlyException("login_expired", "领取或查询听时需要有效的登录态", definitive = true)
        if (!Regex("^[1-9][0-9]*$").matches(account.uid)) {
            throw XmlyException("auth_required", "登录态缺少有效账号标识", definitive = true)
        }
        return account.uid
    }

    private fun result(response: JSONObject): JSONObject {
        val data = response.optJSONObject("data")
            ?: throw XmlyException("invalid_response", "福利响应缺少明确结果")
        val success = data.opt("success") as? Boolean
            ?: throw XmlyException("invalid_response", "福利响应缺少明确结果")
        if (!success) {
            // The server explains *why* (daily cap, balance cap, expired task);
            // replacing it with a generic hint hid the only useful information.
            val reason = listOf(
                data.optString("msg"),
                data.optString("message"),
                response.optString("msg"),
            ).firstOrNull { it.isNotEmpty() }
            val code = data.opt("code")?.toString()
            val detail = reason ?: "福利请求未成功，请在官方 App 检查"
            throw XmlyException(
                "reward_rejected",
                if (code == null) detail else "$detail（业务码 $code）",
                definitive = true,
            )
        }
        return data
    }

    /** Reads the current welfare task list (ad-free tasks included as returned). */
    fun tasks(): JSONObject {
        uid()
        val response = client.request(
            "GET",
            XmlyClient.WELFARE,
            "$PATH/queryVipDurationTask/ts-${clock()}",
        )
        return JSONObject().put("data", result(response))
    }

    /** Claims once, using only fields the caller actually observed. */
    fun claim(fields: Map<String, Any?> = emptyMap(), allowed: () -> Boolean = { true }): JSONObject {
        // long: 服务端对领完后的额外请求也可能只返回“参数校验”，先用当天领取记录阻止重复写入。
        if (allReceivedToday(tasks().getJSONObject("data"), clock())) {
            throw XmlyException("reward_complete", "今日听时奖励已全部领取，请明天再来", definitive = true)
        }
        // long: 查询任务期间用户可能关闭自动领取，写请求发出前再检查一次，避免多领下一档。
        if (!allowed()) throw XmlyException("reward_disabled", "自动领取已停止", definitive = true)
        val account = client.credentials
            ?: throw XmlyException("login_expired", "领取听时需要有效的登录态", definitive = true)
        val owner = uid()
        val payload = normalize(fields)
        val requestId = UUID.randomUUID().toString()
        val timestamp = clock()
        val parts = StringBuilder("$requestId&$owner&$timestamp")
        payload["isSecondPhaseCompleted"]?.let { parts.append("&").append(it) }
        payload["requestId"] = requestId
        payload["uid"] = owner.toLong()
        payload["ts"] = timestamp
        payload["signature"] = md5("$parts&$SALT")
        payload["ticket"] = XmlyCrypto.freshTicket(
            account,
            attributes = "b=lite&s=lite_time_reward&u=$owner",
        )
        val response = client.request(
            "POST",
            XmlyClient.WELFARE,
            "$PATH/rewardDuration/ts-$timestamp",
            json = payload,
            readOnly = false,
        )
        return JSONObject().put("data", result(response))
    }

    /** long: 每次写入前重新查询领取记录与开关，已领完、用户关闭或结果未知时结束这一轮。 */
    fun refresh(enabled: () -> Boolean, quota: () -> Quota): Quota {
        var current = quota()
        lastError = ""
        shouldPause = false
        if (!enabled()) return current
        for (round in 0 until MAX_CLAIMS) {
            if (!enabled()) break
            val before = current.remainingSeconds
            try {
                claim(allowed = enabled)
                current = quota()
            } catch (error: XmlyException) {
                if (error.code in setOf("reward_complete", "reward_disabled")) break
                lastError = error.message ?: "领取听时未完成"
                shouldPause = !error.definitive || error.code == "reward_rejected"
                break
            } catch (_: Exception) {
                lastError = "领取听时未完成，请在官方 App 核对"
                shouldPause = true
                break
            }
            if (current.remainingSeconds <= before) break
        }
        return current
    }

    /**
     * Welfare tiers as reported by the server.
     *
     * `tierDuration` is what one claim of that tier adds, and `receiveTime` is
     * when it was last granted, so the list doubles as a claim log.
     */
    fun tiers(): List<RewardTier> {
        val data = tasks().optJSONObject("data") ?: return emptyList()
        val list = data.optJSONArray("tierList") ?: return emptyList()
        val result = ArrayList<RewardTier>(list.length())
        for (index in 0 until list.length()) {
            val row = list.optJSONObject(index) ?: continue
            val seconds = (row.opt("tierDuration") as? Number)?.toInt() ?: continue
            if (seconds <= 0) continue
            result += RewardTier(
                index = (row.opt("index") as? Number)?.toInt() ?: index,
                seconds = seconds,
                label = row.optString("tierDurationDesc").ifEmpty { "${seconds / 60} 分钟" },
                status = (row.opt("status") as? Number)?.toInt() ?: -1,
                receiveTime = (row.opt("receiveTime") as? Number)?.toLong() ?: 0L,
            )
        }
        return result.sortedBy { it.seconds }
    }

    /** long: 诊断只复用白名单结果摘要，不能把任务响应中的身份或广告票据直接导出。 */
    fun taskDiagnostics(): String {
        return AccountRequestDiagnostics.event(
            0, "GET", "$PATH/queryVipDurationTask", null, client.credentials,
            status = 200, response = tasks().toString(),
        )
    }

    private fun normalize(fields: Map<String, Any?>): LinkedHashMap<String, Any?> {
        val payload = linkedMapOf<String, Any?>()
        for (key in FIELDS) {
            if (!fields.containsKey(key)) continue
            val value = fields[key]
            if (key != "sourceName" && value == null) continue
            if (key == "verifyStr" && (value == null || value == "" || value == false)) continue
            when (value) {
                is String -> if (value.length > 8192) {
                    throw XmlyException("invalid_reward", "福利请求字段过长", definitive = true)
                }

                is Double -> if (!value.isFinite()) {
                    throw XmlyException("invalid_reward", "福利请求字段格式无效", definitive = true)
                }

                is Number, is Boolean, null -> Unit
                else -> throw XmlyException("invalid_reward", "福利请求字段格式无效", definitive = true)
            }
            payload[key] = value
        }
        return payload
    }

    companion object {
        internal fun allReceivedToday(data: JSONObject, nowMillis: Long): Boolean {
            val tiers = data.optJSONArray("tierList") ?: return false
            if (tiers.length() == 0) return false
            val zone = java.time.ZoneId.of("Asia/Shanghai")
            val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            return (0 until tiers.length()).all { index ->
                val row = tiers.optJSONObject(index) ?: return@all false
                val received = (row.opt("receiveTime") as? Number)?.toLong() ?: return@all false
                // long: 只信有效的实际领取时间，不把未核实的 status 枚举当作领取资格。
                received > 0 && java.time.Instant.ofEpochMilli(received).atZone(zone).toLocalDate() == today
            }
        }
        private const val PATH = "/lite-earn/ting/welfare"
        private const val SALT = "0ab3443b71834b33be675af744d76ef0aaa74cd2b60c4b67"
        private val FIELDS = listOf(
            "adId",
            "adResponseId",
            "isUnisdk",
            "ecpm",
            "verifyStr",
            "isSecondPhaseCompleted",
            "sourceName",
        )
        /** Upper bound on one refill pass, so a stuck balance cannot loop forever. */
        private const val MAX_CLAIMS = 64

        internal fun md5(value: String): String =
            MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
