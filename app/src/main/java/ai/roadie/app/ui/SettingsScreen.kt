package ai.roadie.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ai.roadie.core.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun SettingsScreen(value: Preferences, allowed: Boolean, endpoint: String, tokenConfigured: Boolean,
    onUpdate: (Preferences) -> Unit, onConnection: (String, String) -> Unit, onDeleteMemory: () -> Unit, onBack: () -> Unit) {
    var url by remember(endpoint) { mutableStateOf(endpoint) }
    var token by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("우리의 대화 방식", style = MaterialTheme.typography.headlineLarge)
        if (!allowed) {
            Text("정차 상태가 확인되면 설정을 바꿀 수 있어요.", style = MaterialTheme.typography.bodyLarge)
            RoadieButton("돌아가기", onClick = onBack)
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Setting("밤 테마", "눈부심을 줄인 어두운 화면", value.theme == Theme.NIGHT) { onUpdate(value.copy(theme = if (it) Theme.NIGHT else Theme.DAY)) }
            Text("관심사", style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Topic.entries.forEach { topic -> FilterChip(selected = topic in value.interests,
                    onClick = { onUpdate(value.copy(interests = if (topic in value.interests) value.interests - topic else value.interests + topic)) },
                    label = { Text(topic.label) }, modifier = Modifier.heightIn(min = 56.dp)) }
            }
            Setting("로디가 먼저 말 걸기", "대화 사이의 대기 중 20분마다 짧게 물어요. 조용한 동행과 마이크 끄기 중에는 말하지 않아요.", value.proactive) { onUpdate(value.copy(proactive = it)) }
            Setting("대화 후 이어서 듣기", "답변 후와 일시적인 오디오 안내 종료 후에 마이크를 다시 켜요. 끄면 버튼으로만 다시 들어요.", value.resumeListening) { onUpdate(value.copy(resumeListening = it)) }
            Setting("최근 대화 기억에 동의", "최근 대화 3회 분량을 이 기기에 암호화해 저장하고 다음 대화에 사용해요. 끄거나 지우면 삭제돼요. 서버에는 영구 저장하지 않아요.", value.remember) { onUpdate(value.copy(remember = it)) }
            RoadieButton("기억 지우기", primary = false, onClick = onDeleteMemory)
            Setting("2시간 동행 후 휴식 제안", "앱에서 대화를 시작한 후의 경과 시간이에요. 실제 차량 운행 시간이나 졸음 감지 결과가 아니에요.", value.restReminder) { onUpdate(value.copy(restReminder = it)) }
            Setting("명확한 데모 모드", "음성 녹음, AI 응답, 실제 장소 안내를 실행하지 않고 조작 흐름을 살펴봐요.", value.mode == Mode.DEMO) { onUpdate(value.copy(mode = if (it) Mode.DEMO else Mode.LIVE)) }
            HorizontalDivider()
            Text("서버 연결", style = MaterialTheme.typography.bodyLarge)
            Text("모델 API 키는 앱에 입력하지 마세요. 서버에서 발급한 개발용 접근 토큰을 사용해요.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(url, { url = it }, label = { Text("HTTPS 서버 주소") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(token, { token = it }, label = { Text(if (tokenConfigured) "새 접근 토큰, 비우면 기존 값 유지" else "서버 접근 토큰") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            RoadieButton("서버 연결 저장", onClick = { onConnection(url.trim(), token.trim()); token = "" })
            Text("음성은 Pleos 기기 내 엔진을 사용해요. 호출어와 동시에 듣고 말하는 기능은 지원하지 않아요. 음성 중단 버튼으로 말을 끊을 수 있어요.", style = MaterialTheme.typography.bodyMedium)
        }
        RoadieButton("이대로 돌아가기", modifier = Modifier.fillMaxWidth(), onClick = onBack)
    }
}

@Composable private fun Setting(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked, onCheckedChange = onChange, modifier = Modifier.sizeIn(minWidth = 64.dp, minHeight = 64.dp).semantics { contentDescription = title })
        }
    }
}

@Composable fun WelcomeScreen(allowed: Boolean, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("길 위의 다정한\n말동무, 로디", style = MaterialTheme.typography.headlineLarge)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("your favorite passenger.", fontFamily = Brand, style = MaterialTheme.typography.bodyLarge)
            Text("혼자 오래 운전하는 길에 짧은 대화와 관심사 소식을 전해요. 운전과 중요한 안내가 먼저예요.", style = MaterialTheme.typography.bodyLarge)
            Text("듣기를 누르면 Pleos 음성 서비스가 마이크를 사용해요. 인식된 문장은 설정한 서버와 AI 제공자에게 전달돼요. 마이크 끄기, 조용히 가기, 앱을 벗어나기는 녹음과 출력을 멈춰요.")
            Text("대화 기억과 시간에 따른 휴식 제안은 기본으로 꺼져 있어요. 정차 중 설정에서 선택할 수 있어요.")
            Text("졸리거나 피곤하면 안전한 곳에 정차해서 쉬어요. 로디는 졸음을 감지하거나 대화로 해소하는 서비스가 아니에요.")
            if (!allowed) Text("차량 정차 상태를 확인하고 있어요. 주행 중에는 시작 설정을 바꿀 수 없어요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RoadieButton("안내를 확인했어요", enabled = allowed, modifier = Modifier.fillMaxWidth(), onClick = onContinue)
    }
}
