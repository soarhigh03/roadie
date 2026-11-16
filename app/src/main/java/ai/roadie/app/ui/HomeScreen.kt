package ai.roadie.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.roadie.app.R
import ai.roadie.core.*

fun Phase.label(): String = when (this) {
    Phase.READY -> "함께 갈 준비"
    Phase.LISTENING -> "듣고 있어요"
    Phase.THINKING -> "생각하고 있어요"
    Phase.SPEAKING -> "로디가 말하는 중"
    Phase.WAITING -> "이야기를 기다려요"
    Phase.QUIET -> "조용한 동행"
    Phase.MUTED -> "마이크 꺼짐"
    Phase.PAUSED -> "오디오 안내 대기"
    Phase.REST -> "쉬어가도 괜찮아요"
    Phase.BRIEFING -> "관심사 브리핑"
    Phase.ERROR -> "연결 확인이 필요해요"
}
private fun Phase.title(): String = when (this) {
    Phase.READY -> "오늘도\n같이 갈까요?"
    Phase.LISTENING -> "응, 듣고 있어요."
    Phase.THINKING -> "잠깐만요.\n생각하고 있어요."
    Phase.SPEAKING -> "이야기를\n이어가 볼까요?"
    Phase.WAITING -> "당신의 속도로\n함께 갈게요."
    Phase.QUIET -> "말없이 함께 가도\n괜찮아요."
    Phase.MUTED -> "마이크가\n꺼져 있어요."
    Phase.PAUSED -> "길에 집중하세요.\n여기서 기다릴게요."
    Phase.REST -> "가까운 곳에서\n쉬어갈까요?"
    Phase.BRIEFING -> "오늘의 관심사,\n짧게 들려줄게요."
    Phase.ERROR -> "잠깐, 연결을\n확인해주세요."
}

@Composable fun RoadieButton(label: String, primary: Boolean = true, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 72.dp),
        shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) Lime else MaterialTheme.colorScheme.surface,
            contentColor = if (primary) Ink else MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )) { Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge) }
}

@Composable fun StatusPill(phase: Phase) {
    val color = when (phase) {
        Phase.QUIET, Phase.MUTED, Phase.PAUSED, Phase.WAITING -> Silver
        Phase.REST, Phase.ERROR -> Color(0xFFFFD29A)
        else -> Lime
    }
    Row(Modifier.clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp, 10.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Text(phase.label(), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable private fun Companion(phase: Phase, night: Boolean, modifier: Modifier = Modifier) {
    val quiet = phase in setOf(Phase.QUIET, Phase.MUTED, Phase.PAUSED)
    val artwork = when (phase) {
        Phase.SPEAKING -> R.drawable.roadie_speaking
        Phase.QUIET, Phase.MUTED, Phase.PAUSED -> R.drawable.roadie_quiet
        Phase.REST, Phase.ERROR -> R.drawable.roadie_rest
        else -> R.drawable.roadie_original
    }
    Box(modifier.clip(RoundedCornerShape(32.dp)).background(if (night) Color(0xFF22252B) else Color(0xFFFBE0EF))
        .border(2.dp, if (quiet) Silver.copy(alpha = .4f) else Pink.copy(alpha = .5f), RoundedCornerShape(32.dp)), contentAlignment = Alignment.Center) {
        Image(painterResource(artwork), "입체 로디, ${phase.label()}",
            Modifier.fillMaxSize().padding(if (quiet) 32.dp else 12.dp).clip(RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Fit, alpha = if (night) .76f else 1f)
    }
}

data class HomeActions(
    val start: () -> Unit, val listen: () -> Unit, val mute: () -> Unit, val quiet: () -> Unit,
    val end: () -> Unit, val retry: () -> Unit, val briefing: () -> Unit, val play: () -> Unit,
    val stopBriefing: () -> Unit, val next: () -> Unit, val nextTopic: () -> Unit,
    val rest: () -> Unit, val findRest: () -> Unit, val settings: () -> Unit, val sources: () -> Unit,
    val permissions: () -> Unit, val demo: (String) -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable fun HomeScreen(state: SessionState, preferences: Preferences, settingsAllowed: Boolean, detailsAllowed: Boolean, actions: HomeActions) {
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        val compact = maxWidth < 720.dp
        val short = maxHeight < 620.dp
        val pad = if (compact || short) 20.dp else 40.dp
        Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(if (short) 12.dp else 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("roadie", fontFamily = Brand, fontWeight = FontWeight.Bold, fontSize = if (compact) 36.sp else 45.sp, letterSpacing = (-2.7).sp)
                StatusPill(state.phase)
            }
            if (preferences.mode == Mode.DEMO) Text("데모 모드  /  음성 녹음과 AI 연결 없음", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            val article = state.briefing?.articles?.getOrNull(state.articleIndex)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (compact) {
                    if (!short) Companion(state.phase, preferences.theme == Theme.NIGHT, Modifier.height(180.dp).fillMaxWidth())
                    Message(state, article, compact = true)
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1.25f)) { Message(state, article, compact = false) }
                    Companion(state.phase, preferences.theme == Theme.NIGHT, Modifier.weight(1f).height(if (short) 210.dp else 330.dp))
                }
                if (preferences.mode == Mode.DEMO && state.active && settingsAllowed) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { actions.demo("오늘 길이 예뻐") }, modifier = Modifier.heightIn(min = 56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("데모 발화 보내기") }
                    OutlinedButton(onClick = { actions.demo("졸려서 쉬고 싶어") }, modifier = Modifier.heightIn(min = 56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("피로 표현 예시") }
                }
                if (state.active) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(actions.briefing, enabled = state.phase != Phase.MUTED, modifier = Modifier.heightIn(min = 56.dp)) { Text("관심사 브리핑", color = MaterialTheme.colorScheme.onBackground) }
                    TextButton(actions.rest, modifier = Modifier.heightIn(min = 56.dp)) { Text("쉬어가기", color = MaterialTheme.colorScheme.onBackground) }
                    TextButton(actions.end, modifier = Modifier.heightIn(min = 56.dp)) { Text("대화 끝내기", color = MaterialTheme.colorScheme.onBackground) }
                }
                if (article != null && state.phase in setOf(Phase.BRIEFING, Phase.SPEAKING)) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(actions.nextTopic, modifier = Modifier.heightIn(min = 56.dp)) { Text("다음 관심사", color = MaterialTheme.colorScheme.onBackground) }
                    TextButton(actions.sources, enabled = detailsAllowed, modifier = Modifier.heightIn(min = 56.dp)) { Text("출처와 게시 시각", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            val main: Pair<String, () -> Unit> = when (state.phase) {
                Phase.READY -> "함께 출발하기" to actions.start
                Phase.LISTENING -> "마이크 끄기" to actions.mute
                Phase.THINKING, Phase.SPEAKING -> if (state.briefing != null && state.phase == Phase.SPEAKING) "브리핑 멈추기" to actions.stopBriefing else "말 끊고 이야기하기" to actions.listen
                Phase.MUTED -> "마이크 켜기" to actions.listen
                Phase.REST -> "휴식 장소 찾기" to actions.findRest
                Phase.ERROR -> if (state.permissionDenied) "권한 확인하기" to actions.permissions else "다시 연결하기" to actions.retry
                Phase.BRIEFING -> "브리핑 듣기" to actions.play
                else -> "다시 이야기하기" to actions.listen
            }
            val secondary = when (state.phase) {
                Phase.READY -> "대화 설정" to actions.settings
                Phase.QUIET, Phase.MUTED, Phase.PAUSED, Phase.REST -> "대화 끝내기" to actions.end
                Phase.BRIEFING -> "다음 소식" to actions.next
                else -> "조용히 가기" to actions.quiet
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RoadieButton(main.first, modifier = Modifier.weight(1f), enabled = !(state.phase == Phase.ERROR && !state.retryable && !state.permissionDenied) && !(state.phase == Phase.BRIEFING && article == null), onClick = main.second)
                RoadieButton(secondary.first, primary = false, modifier = Modifier.weight(1f), enabled = state.phase != Phase.READY || settingsAllowed, onClick = secondary.second)
            }
            Text(when {
                state.phase == Phase.READY && !settingsAllowed -> "정차 상태가 확인되면 설정을 바꿀 수 있어요."
                state.phase == Phase.LISTENING -> "마이크 사용 중. 조용히 가기를 누르면 녹음을 멈춰요."
                state.phase == Phase.MUTED -> "마이크가 꺼져 있어요. 호출어로 켜지지 않아요."
                state.phase == Phase.BRIEFING -> "출처와 게시 시각은 정차 후 확인할 수 있어요."
                else -> "버튼으로 대화를 이어가요. 졸리면 안전한 곳에서 쉬어요."
            }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun Message(state: SessionState, article: Article?, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(state.phase.title(), style = if (compact) MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, lineHeight = 44.sp) else MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp, lineHeight = 60.sp))
        Text(if (state.partial.isNotBlank()) state.partial else state.message,
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (state.phase == Phase.SPEAKING) 3 else 5, overflow = TextOverflow.Ellipsis)
        if (article != null && state.phase == Phase.BRIEFING) {
            Text("${state.articleIndex + 1} / ${state.briefing!!.articles.size}   ${state.briefing?.topic?.label.orEmpty()}", fontFamily = Mono, fontSize = 18.sp)
            Text(if (article.stale) "지난 소식  /  실시간 정보 아님" else "RSS 헤드라인  /  실시간 정보 아님", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.phase == Phase.LISTENING) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(8, 18, 30, 40, 24, 34, 18, 10).forEach { Box(Modifier.width(6.dp).height(it.dp).background(Lime, RoundedCornerShape(4.dp))) }
        }
    }
}
