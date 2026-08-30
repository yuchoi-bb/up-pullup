package com.pullup.tracker.ai

import com.pullup.tracker.data.PlanSession
import com.pullup.tracker.data.SessionLog
import com.pullup.tracker.data.TrainingPlan
import org.json.JSONArray
import org.json.JSONObject

/** AI 코치가 만들어 준 플랜의 원본 형태. */
data class GeneratedPlan(
    val name: String,
    val exercise: String,
    val goal: String,
    val advice: String,
    val sessions: List<PlanSession>
)

object CoachPrompts {

    const val SYSTEM = """너는 근력 운동 프로그램을 설계하는 코치다.
사용자의 현재 수행 능력에서 목표까지 무리 없이 도달하는 세션 단위 계획을 만든다.
원칙:
- 세션당 총량 증가는 5% 이내로 완만하게. 주 1회는 증가 없이 다지는 날을 둔다.
- 세트 수는 사용자가 지정한 값을 유지한다.
- 마지막 세션은 정확히 목표 수치와 같아야 한다.
- 모든 숫자는 정수. 설명은 한국어로 짧게."""

    fun planPrompt(
        exercise: String,
        currentSets: List<Int>,
        goalPerSet: Int,
        setCount: Int,
        daysPerWeek: Int,
        weeks: Int?,
        extraNote: String
    ): String {
        val period = weeks?.let { "$it 주 안에" } ?: "적당한 기간 안에"
        return """
운동: $exercise
현재 한 번의 운동에서 하는 세트별 개수: ${currentSets.joinToString(", ")}
목표: ${setCount}세트 × ${goalPerSet}개 (총 ${setCount * goalPerSet}개)
훈련 빈도: 주 ${daysPerWeek}회
기간: $period 목표 달성
추가 요청: ${extraNote.ifBlank { "없음" }}

아래 JSON 스키마로만 답하라. 다른 텍스트를 덧붙이지 마라.
{
  "name": "계획 이름",
  "exercise": "$exercise",
  "goal": "목표 요약 한 줄",
  "advice": "실행 팁 2~3문장",
  "sessions": [
    { "targets": [세트별 개수 정수 배열], "note": "짧은 메모(선택)" }
  ]
}
sessions는 첫 세션이 현재 수준과 같거나 아주 조금 높은 값에서 시작해, 마지막 세션이 목표와 정확히 같아야 한다.
sessions의 각 targets 길이는 반드시 ${setCount}이다.
""".trim()
    }

    fun coachPrompt(plan: TrainingPlan?, next: PlanSession?, recentLogs: List<SessionLog>, question: String): String {
        val history = recentLogs.take(14).joinToString("\n") { log ->
            "- ${log.date}: ${log.exercise} ${log.repsText} (총 ${log.total}개 / 목표 ${log.targetTotal}개)"
        }.ifBlank { "- 아직 기록 없음" }
        return """
현재 계획: ${plan?.name ?: "없음"} (${plan?.goal ?: "-"})
다음 세션 목표: ${next?.targets?.joinToString("/") ?: "-"} (총 ${next?.total ?: 0}개)
최근 기록:
$history

질문: $question

한국어로 5문장 이내, 실행 가능한 조언 위주로 답하라.
""".trim()
    }

    /** Gemini가 돌려준 JSON을 파싱한다. 코드펜스가 섞여 와도 처리한다. */
    fun parsePlan(raw: String, expectedSetCount: Int): GeneratedPlan {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        require(start >= 0 && end > start) { "AI 응답에서 JSON을 찾지 못했습니다." }
        val obj = JSONObject(cleaned.substring(start, end + 1))
        val array: JSONArray = obj.optJSONArray("sessions")
            ?: throw IllegalStateException("AI 응답에 sessions가 없습니다.")

        val sessions = (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val targetsArray = item.optJSONArray("targets") ?: return@mapNotNull null
            val targets = (0 until targetsArray.length()).map { targetsArray.optInt(it, 0) }
                .filter { it > 0 }
            if (targets.isEmpty()) return@mapNotNull null
            PlanSession(
                index = i + 1,
                targets = targets,
                kind = PlanSession.KIND_PROGRESS,
                note = item.optString("note", "")
            )
        }.filter { it.targets.size == expectedSetCount }

        require(sessions.isNotEmpty()) { "AI가 만든 세션이 올바르지 않습니다. 다시 시도해 주세요." }

        val fixed = sessions.mapIndexed { i, s ->
            when (i) {
                0 -> s.copy(kind = PlanSession.KIND_START)
                sessions.lastIndex -> s.copy(kind = PlanSession.KIND_GOAL)
                else -> s
            }
        }

        return GeneratedPlan(
            name = obj.optString("name", "AI 생성 플랜"),
            exercise = obj.optString("exercise", ""),
            goal = obj.optString("goal", ""),
            advice = obj.optString("advice", ""),
            sessions = fixed
        )
    }
}
