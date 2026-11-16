package kr.yeaan.bus.data.remote

import com.google.gson.*
import java.io.IOException

class ApiFailure(message: String) : IOException(message)

object TagoErrors {
    fun failure(code: String): ApiFailure = ApiFailure(when (code) {
        "30" -> "TAGO에서 인증키를 확인하지 못했습니다. 공공데이터포털의 인증키와 해당 서비스 활용 승인 상태를 확인해 주세요. (30)"
        "20" -> "TAGO 서비스 이용 권한이 없습니다. 해당 버스정보 서비스의 활용 신청과 승인 상태를 확인해 주세요. (20)"
        "31" -> "TAGO 인증키의 이용 기간이 만료되었습니다. 공공데이터포털에서 갱신해 주세요. (31)"
        "32" -> "TAGO 인증키 또는 등록된 접근 IP를 확인해 주세요. (32)"
        "22" -> "오늘의 TAGO 조회 한도를 초과했습니다. (22)"
        "23" -> "TAGO 요청이 잠시 몰렸습니다. 잠시 후 다시 시도해 주세요. (23)"
        else -> "TAGO 서비스에서 오류가 발생했습니다." // Do not echo unknown server-supplied strings.
    })
    fun gatewayCode(root: JsonObject): String? = runCatching {
        root.getAsJsonObject("OpenAPI_ServiceResponse")?.getAsJsonObject("cmmMsgHeader")?.text("returnReasonCode")
    }.getOrNull()?.takeIf { it.isNotBlank() }

    fun httpFailure(status: Int, body: String): ApiFailure {
        val code = runCatching {
            val root = JsonParser.parseString(body).asJsonObject
            gatewayCode(root) ?: root.getAsJsonObject("response")?.getAsJsonObject("header")?.text("resultCode")
        }.getOrNull() ?: Regex("<returnReasonCode>\\s*(\\d{1,3})\\s*</returnReasonCode>").find(body)?.groupValues?.get(1)
        return if (code != null && code != "00" && code != "0") failure(code)
        else ApiFailure(if (status == 401 || status == 403)
            "TAGO 인증이 거부되었습니다. 인증키와 서비스 활용 승인 상태를 확인해 주세요. (HTTP $status)"
        else "TAGO 서버에 연결할 수 없습니다. (HTTP $status)")
    }
}
fun JsonObject.text(key: String): String = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
fun JsonObject.number(key: String): Double? = text(key).toDoubleOrNull()?.takeIf { it.isFinite() }
fun JsonObject.integer(key: String): Int? = number(key)?.toInt()
fun JsonArray.objects(): List<JsonObject> = mapNotNull { it.takeIf { element -> element.isJsonObject }?.asJsonObject }

/** TAGO serializes 0 items as "", one as an object, and many as an array. */
object TagoParser {
    fun items(root: JsonObject): List<JsonObject> {
        TagoErrors.gatewayCode(root)?.let { throw TagoErrors.failure(it) }
        val response = root.getAsJsonObject("response") ?: throw ApiFailure("TAGO 응답 형식이 변경되었습니다.")
        val header = response.getAsJsonObject("header") ?: throw ApiFailure("TAGO 응답 헤더가 없습니다.")
        val code = header.text("resultCode")
        if (code != "00" && code != "0") {
            throw TagoErrors.failure(code)
        }
        val body = response.getAsJsonObject("body") ?: throw ApiFailure("TAGO 본문이 없습니다.")
        val items = body.get("items") ?: return emptyList()
        if (!items.isJsonObject) return emptyList()
        val item = items.asJsonObject.get("item") ?: return emptyList()
        return when {
            item.isJsonArray -> item.asJsonArray.objects()
            item.isJsonObject -> listOf(item.asJsonObject)
            else -> emptyList()
        }
    }
    fun total(root: JsonObject): Int = root.getAsJsonObject("response")?.getAsJsonObject("body")?.integer("totalCount") ?: 0
}
