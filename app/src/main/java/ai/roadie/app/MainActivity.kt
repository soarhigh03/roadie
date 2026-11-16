package ai.roadie.app

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.roadie.app.ui.*
import ai.roadie.core.*

class MainActivity : ComponentActivity() {
    private val vm: RoadieViewModel by viewModels()
    private var permissionAction: (() -> Unit)? = null
    private var permissionIsSpeech = false
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) permissionAction?.invoke()
        else if (permissionIsSpeech) vm.conversation.permissionDenied()
        else vm.notice.value = "위치 권한이 없어 휴식 장소를 찾을 수 없어요. 정차 후 앱 권한에서 허용할 수 있어요."
        permissionAction = null
    }
    private fun request(names: List<String>, speech: Boolean, action: () -> Unit) {
        val runtime = names.filter { name ->
            val info = runCatching { packageManager.getPermissionInfo(name, 0) }.getOrNull()
            info != null && info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE == PermissionInfo.PROTECTION_DANGEROUS && checkSelfPermission(name) != PackageManager.PERMISSION_GRANTED
        }
        if (runtime.isEmpty()) action()
        else { permissionAction = action; permissionIsSpeech = speech; permissions.launch(runtime.toTypedArray()) }
    }
    private fun listen() {
        if (vm.preferences.value.mode == Mode.DEMO) vm.conversation.resume()
        else request(listOf("pleos.car.permission.STT_SERVICE", "pleos.car.permission.TTS_SERVICE"), true) { vm.conversation.resume() }
    }
    private fun appPermissions() {
        if (!vm.access.value.settingsAllowed) { vm.notice.value = "권한 설정은 정차 후 확인해주세요."; return }
        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            .onFailure { vm.notice.value = "차량 설정의 앱 권한에서 로디를 찾아주세요." }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs by vm.preferences.collectAsStateWithLifecycle()
            val session by vm.session.collectAsStateWithLifecycle()
            val access by vm.access.collectAsStateWithLifecycle()
            val loaded by vm.loaded.collectAsStateWithLifecycle()
            val notice by vm.notice.collectAsStateWithLifecycle()
            val places by vm.places.collectAsStateWithLifecycle()
            val busy by vm.placesBusy.collectAsStateWithLifecycle()
            var page by rememberSaveable { mutableStateOf("home") }
            RoadieTheme(prefs.theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                    if (!loaded) Box(Modifier.padding(40.dp)) { Text("로디를 준비하고 있어요.") }
                    else if (!prefs.onboarded) WelcomeScreen(access.settingsAllowed) { vm.updatePreferences(prefs.copy(onboarded = true)); page = "settings" }
                    else when (page) {
                        "settings" -> SettingsScreen(prefs, access.settingsAllowed, vm.vault.endpoint, vm.vault.accessToken.isNotBlank(),
                            vm::updatePreferences, vm::saveConnection, vm::clearMemory, { page = "home" })
                        "sources" -> SourcesScreen(session.briefing, access.detailsAllowed) { page = "home" }
                        "locationConsent" -> Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            Text("가까운 곳에서\n쉬어갈까요?", style = MaterialTheme.typography.headlineLarge)
                            Text("주변 휴게소를 찾기 위해 이번 위치를 서버와 Kakao Local에 보내요. 위치를 저장하지 않아요. 결과는 도로 방향과 다를 수 있어요.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            RoadieButton("이번 위치로 휴게소 찾기", modifier = Modifier.fillMaxWidth()) {
                                page = "places"
                                request(listOf("pleos.car.permission.FUSED_LOCATION"), false) { vm.findRest() }
                            }
                            RoadieButton("돌아가기", primary = false, modifier = Modifier.fillMaxWidth()) { page = "home" }
                        }
                        "places" -> PlacesScreen(places, busy, vm::navigate) { vm.cancelPlaces(); page = "home" }
                        else -> HomeScreen(session, prefs, access.settingsAllowed, access.detailsAllowed, HomeActions(
                            start = ::listen, listen = ::listen, mute = vm.conversation::mute, quiet = vm.conversation::quiet,
                            end = vm::end, retry = vm.conversation::retry, briefing = { vm.conversation.loadBriefing() },
                            play = vm.conversation::playBriefing, stopBriefing = vm.conversation::stopBriefing,
                            next = vm.conversation::nextArticle, nextTopic = vm.conversation::nextTopic,
                            rest = vm.conversation::offerRest, findRest = { vm.conversation.quiet(); page = "locationConsent" },
                            settings = { if (access.settingsAllowed) page = "settings" }, sources = { if (access.detailsAllowed) page = "sources" },
                            permissions = ::appPermissions, demo = { if (access.settingsAllowed) vm.conversation.demoInput(it) },
                        ))
                    }
                    }
                    if (notice != null) Surface(color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(notice!!, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            RoadieButton("확인", primary = false) { vm.notice.value = null }
                        }
                    }
                    }
                }
            }
        }
    }
    override fun onStop() {
        if (!isChangingConfigurations) { permissionAction = null; vm.background() }
        super.onStop()
    }
}
