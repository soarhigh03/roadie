package ai.roadie.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.roadie.core.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun date(value: String) = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.systemDefault()).format(Instant.parse(value))
}.getOrDefault("게시 시각 확인 필요")

@Composable fun SourcesScreen(briefing: Briefing?, allowed: Boolean, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("소식의 출처", style = MaterialTheme.typography.headlineLarge)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (!allowed) Text("자세한 출처는 정차 후 확인해주세요.")
            else if (briefing == null) Text("아직 가져온 브리핑이 없어요.")
            else {
                Text("가져온 시각: ${date(briefing.fetchedAt)}\n실시간 교통이나 운전 판단에 사용하지 마세요.")
                briefing.articles.forEach { article ->
                    Surface(shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(article.title, style = MaterialTheme.typography.bodyLarge)
                            Text("${article.source}\n게시: ${date(article.publishedAt)}")
                            Text(if (article.summaryKind == "demo") "데모 데이터, 실제 뉴스 아님" else "출처가 제공한 헤드라인 발췌")
                            if (article.stale) Text("게시 후 24시간이 지난 소식이에요.")
                            if (article.url.isNotBlank()) Text(article.url, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        RoadieButton("돌아가기", modifier = Modifier.fillMaxWidth(), onClick = onBack)
    }
}

@Composable fun PlacesScreen(places: List<Place>, busy: Boolean, onNavigate: (Place) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("가까운 휴식 장소", style = MaterialTheme.typography.headlineLarge)
        Text("도로 방향과 진입 가능 여부를 차량 내비게이션에서 확인해주세요. 장소를 누르면 현재 경로를 변경할 수 있어요.")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (busy) Text("차량 위치와 안내 연결을 확인하고 있어요.")
            else if (places.isEmpty()) Text("조회된 장소가 없어요.")
            places.take(3).forEach { place ->
                RoadieButton("${place.name}\n약 ${place.distanceMeters / 1000.0} km, 직선거리", enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { onNavigate(place) })
            }
        }
        Text("장소 정보: Kakao Local", style = MaterialTheme.typography.labelMedium)
        RoadieButton("돌아가기", primary = false, modifier = Modifier.fillMaxWidth(), onClick = onBack)
    }
}
