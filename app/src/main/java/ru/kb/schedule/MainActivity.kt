package ru.kb.schedule

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val Red = Color(0xFFE31E24)
private val Ink = Color(0xFF242428)
private val Muted = Color(0xFF85858D)
private val Canvas = Color(0xFFF4F4F6)
private val Card = Color.White
private val ShiftBlack = Color(0xFF171719)
private val Off = Color(0xFFF0F0F2)
private val EarlyShift = Color(0xFFFFC94A)
private val LateShift = Color(0xFF2384F5)
private val SickDay = Color(0xFFFFE1E1)
private val SickText = Color(0xFFB3261E)
private val VacationDay = Color(0xFFDDF3E4)
private val VacationText = Color(0xFF187443)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContent { KbScheduleApp(getSharedPreferences("kb_schedule", MODE_PRIVATE)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun KbScheduleApp(prefs: android.content.SharedPreferences) {
    val names = remember {
        mutableStateListOf<String>().apply {
            val a = runCatching { JSONArray(prefs.getString("employees", "[]")) }.getOrNull()
            if (a != null) for (i in 0 until a.length()) add(a.optString(i))
            if (isEmpty()) addAll(listOf("Сотрудник 1", "Сотрудник 2", "Сотрудник 3"))
        }
    }
    val employeeIds = remember {
        mutableStateListOf<String>().apply {
            val saved = runCatching { JSONArray(prefs.getString("employee_ids", "[]")) }.getOrNull()
            if (saved != null && saved.length() == names.size) {
                for (i in 0 until saved.length()) add(saved.optString(i))
            } else {
                names.indices.forEach { add("emp_" + System.nanoTime() + "_" + it) }
            }
        }
    }
    val cellText = remember { mutableStateMapOf<String, String>().apply {
        val o = runCatching { JSONObject(prefs.getString("texts", "{}")) }.getOrNull()
        o?.keys()?.forEach { k -> put(k, o.optString(k)) }
    } }
    val cellTypes = remember { mutableStateMapOf<String, Int>().apply {
        val o = runCatching { JSONObject(prefs.getString("types", "{}")) }.getOrNull()
        o?.keys()?.forEach { k -> put(k, o.optInt(k)) }
    } }
    val cellStarts = remember { mutableStateMapOf<String, String>().apply {
        val o = runCatching { JSONObject(prefs.getString("starts", "{}")) }.getOrNull()
        o?.keys()?.forEach { k -> put(k, o.optString(k)) }
    } }
    val cellEnds = remember { mutableStateMapOf<String, String>().apply {
        val o = runCatching { JSONObject(prefs.getString("ends", "{}")) }.getOrNull()
        o?.keys()?.forEach { k -> put(k, o.optString(k)) }
    } }
    // Migrate cells saved by the previous version, where the employee name hash was used as an ID.
    remember {
        names.forEachIndexed { index, name ->
            val oldPrefix = name.hashCode().toString() + "-"
            val newPrefix = employeeIds[index] + "-"
            cellTypes.toMap().forEach { (oldKey, value) ->
                if (oldKey.startsWith(oldPrefix)) {
                    cellTypes[newPrefix + oldKey.removePrefix(oldPrefix)] = value
                    cellTypes.remove(oldKey)
                }
            }
            cellText.toMap().forEach { (oldKey, value) ->
                if (oldKey.startsWith(oldPrefix)) {
                    cellText[newPrefix + oldKey.removePrefix(oldPrefix)] = value
                    cellText.remove(oldKey)
                }
            }
        }
        true
    }
    val employeeModes = remember {
        mutableStateMapOf<String, Int>().apply {
            val saved = runCatching { JSONObject(prefs.getString("employee_modes", "{}")) }.getOrNull()
            employeeIds.forEach { id -> put(id, saved?.optInt(id, 1)?.takeIf { it == 1 || it == 2 } ?: 1) }
        }
    }
    val cellColors = remember {
        mutableStateMapOf<Int, String>().apply {
            val saved = runCatching { JSONObject(prefs.getString("cell_colors", "{}")) }.getOrNull()
            put(0, saved?.optString("0")?.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) } ?: "#F0F0F2")
            put(1, saved?.optString("1")?.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) } ?: "#FFC94A")
            put(2, saved?.optString("2")?.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) } ?: "#2384F5")
            put(3, saved?.optString("3")?.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) } ?: "#FFE1E1")
            put(4, saved?.optString("4")?.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) } ?: "#DDF3E4")
        }
    }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var addDialog by remember { mutableStateOf(false) }
    var colorsDialog by remember { mutableStateOf(false) }
    var colorType by remember { mutableIntStateOf(1) }
    var deleteIndex by remember { mutableStateOf<Int?>(null) }
    var newName by remember { mutableStateOf("") }
    val employeeScroll = rememberScrollState()
    var selectedCell by remember { mutableStateOf<String?>(null) }
    var timePickerCell by remember { mutableStateOf<String?>(null) }
    var timePickerKind by remember { mutableStateOf("С") }
    var totalsDialog by remember { mutableStateOf(false) }
    var editCaptionCell by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                val output = context.contentResolver.openOutputStream(uri) ?: error("Не удалось создать файл")
                output.use {
                    writeScheduleXlsx(it, names.toList(), employeeIds.toList(), month, cellTypes.toMap(), cellText.toMap(), cellStarts.toMap(), cellEnds.toMap())
                }
            }
            Toast.makeText(context, if (result.isSuccess) "Excel-файл сохранён" else "Ошибка экспорта", Toast.LENGTH_LONG).show()
        }
    }

    fun save() {
        val employeeJson = JSONArray().also { a -> names.forEach { a.put(it) } }
        val idsJson = JSONArray().also { a -> employeeIds.forEach { a.put(it) } }
        val textsJson = JSONObject().also { o -> cellText.forEach { (k,v) -> o.put(k,v) } }
        val typesJson = JSONObject().also { o -> cellTypes.forEach { (k,v) -> o.put(k,v) } }
        val modesJson = JSONObject().also { o -> employeeModes.forEach { (k,v) -> o.put(k,v) } }
        val startsJson = JSONObject().also { o -> cellStarts.forEach { (k,v) -> o.put(k,v) } }
        val endsJson = JSONObject().also { o -> cellEnds.forEach { (k,v) -> o.put(k,v) } }
        val colorsJson = JSONObject().also { o -> cellColors.forEach { (k,v) -> o.put(k.toString(),v) } }
        prefs.edit().putString("employees", employeeJson.toString())
            .putString("employee_ids", idsJson.toString())
            .putString("texts", textsJson.toString()).putString("types", typesJson.toString())
            .putString("employee_modes", modesJson.toString())
            .putString("starts", startsJson.toString()).putString("ends", endsJson.toString())
            .putString("cell_colors", colorsJson.toString()).apply()
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Red, background = Canvas, surface = Card, onSurface = Ink)) {
        Scaffold(containerColor = Canvas) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(44.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("КРАСНОЕ & БЕЛОЕ", color = Red, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Text("График смен", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { month = month.minusMonths(1) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.ChevronLeft, null)
                        }
                        Text(month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru")).replaceFirstChar { it.uppercase() } + " " + month.year,
                            textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.widthIn(min = 130.dp))
                        IconButton(onClick = { month = month.plusMonths(1) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                    TextButton(onClick = { totalsDialog = true }, contentPadding = PaddingValues(horizontal = 7.dp)) {
                        Text("Итоги", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { colorsDialog = true }, contentPadding = PaddingValues(horizontal = 7.dp)) {
                        Text("Цвета", color = Color(0xFF5B62A8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = { exportLauncher.launch("График_смен_${month.monthValue}_${month.year}.xlsx") },
                        contentPadding = PaddingValues(horizontal = 7.dp)
                    ) {
                        Text("Excel", color = Color(0xFF168447), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { addDialog = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Default.Add, null, tint = Red)
                        Text("Сотрудник", color = Red, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(5.dp))
                Column(
                    Modifier.fillMaxWidth().weight(1f).background(Card, RoundedCornerShape(12.dp)).padding(5.dp)
                ) {
                    Row(Modifier.fillMaxWidth().height(42.dp)) {
                        Box(Modifier.width(190.dp).fillMaxHeight().border(0.5.dp, Color(0xFFE5E5E8)), contentAlignment = Alignment.CenterStart) {
                            Text("СОТРУДНИК", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.weight(1f).fillMaxHeight()) {
                            for (day in 1..month.lengthOfMonth()) {
                                val date = month.atDay(day)
                                Column(
                                    Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color(0xFFE5E5E8)),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(day.toString(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Ink, maxLines = 1)
                                    Text(
                                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")).take(2).uppercase(Locale("ru")),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Muted,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().weight(1f).verticalScroll(employeeScroll)) {
                        Column(Modifier.width(190.dp)) {
                            names.forEachIndexed { index, name ->
                                val employeeId = employeeIds[index]
                                val mode = employeeModes[employeeId] ?: 1
                                Row(
                                    Modifier.fillMaxWidth().height(66.dp).border(0.5.dp, Color(0xFFE5E5E8)).padding(horizontal = 5.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                                        Text(name, fontWeight = FontWeight.Medium, fontSize = 11.sp, maxLines = 2, lineHeight = 13.sp)
                                        Spacer(Modifier.height(3.dp))
                                        Box(
                                            Modifier.fillMaxWidth().height(23.dp)
                                                .background(Color(android.graphics.Color.parseColor(cellColors[mode] ?: if (mode == 1) "#FFC94A" else "#2384F5")), RoundedCornerShape(6.dp))
                                                .clickable { employeeModes[employeeId] = if (mode == 1) 2 else 1; save() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Смена", color = if (mode == 1) Ink else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    IconButton(onClick = { deleteIndex = index }, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Удалить сотрудника", tint = Red, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            names.forEachIndexed { index, name ->
                                Row(Modifier.fillMaxWidth().height(66.dp)) {
                                    for (day in 1..month.lengthOfMonth()) {
                                        val key = employeeIds[index] + "-" + month.year + "-" + month.monthValue + "-" + day
                                        val shiftType = cellTypes[key] ?: 0
                                        val isShift = shiftType == 1 || shiftType == 2
                                        val label = cellText[key].orEmpty()
                                        val cellColor = Color(android.graphics.Color.parseColor(cellColors[shiftType] ?: when (shiftType) {
                                            1 -> "#FFC94A"
                                            2 -> "#2384F5"
                                            3 -> "#FFE1E1"
                                            4 -> "#DDF3E4"
                                            else -> "#F0F0F2"
                                        }))
                                        Box(
                                            Modifier.weight(1f).fillMaxHeight().padding(1.dp)
                                                .background(cellColor, RoundedCornerShape(4.dp))
                                                .combinedClickable(
                                                    onClick = {
                                                        if (shiftType != 0 || cellStarts.containsKey(key) || cellEnds.containsKey(key)) {
                                                            cellTypes.remove(key)
                                                            cellText.remove(key)
                                                            cellStarts.remove(key)
                                                            cellEnds.remove(key)
                                                        } else {
                                                            cellTypes[key] = employeeModes[employeeIds[index]] ?: 1
                                                            cellText.remove(key)
                                                        }
                                                        save()
                                                    },
                                                    onLongClick = {
                                                        selectedCell = key
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            when (shiftType) {
                                                1, 2 -> {
                                                    val start = cellStarts[key]
                                                    val end = cellEnds[key]
                                                    val timeLabel = when {
                                                        start != null && end != null -> "С $start\nДО $end"
                                                        start != null -> "С $start"
                                                        end != null -> "ДО $end"
                                                        label.isNotBlank() -> label
                                                        else -> "СМЕНА"
                                                    }
                                                    Text(timeLabel, color = if (shiftType == 1) Ink else Color.White,
                                                        fontSize = 6.sp, lineHeight = 7.sp, fontWeight = FontWeight.Bold,
                                                        textAlign = TextAlign.Center, maxLines = 3)
                                                }
                                                3 -> Text("БС", color = SickText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                4 -> Text("ОТ", color = VacationText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(22.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(Color(android.graphics.Color.parseColor(cellColors[1] ?: "#FFC94A")), "Смена")
                    LegendDot(Color(android.graphics.Color.parseColor(cellColors[0] ?: "#F0F0F2")), "Выходной")
                    LegendDot(Color(android.graphics.Color.parseColor(cellColors[3] ?: "#FFE1E1")), "БС")
                    LegendDot(Color(android.graphics.Color.parseColor(cellColors[4] ?: "#DDF3E4")), "ОТ")
                    Text("Долгое нажатие — отсутствие", fontSize = 9.sp, color = Muted)
                }
            }
        }
    }

    if (colorsDialog) {
        val palette = listOf("#FFC94A", "#2384F5", "#35B86B", "#EF5350", "#9B59B6", "#26A69A", "#FF8A3D", "#8D99AE", "#242428", "#F0F0F2")
        val types = listOf(1 to "Смена", 0 to "Выходной", 3 to "БС", 4 to "ОТ")
        AlertDialog(
            onDismissRequest = { colorsDialog = false },
            title = { Text("Цвета графика") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    types.forEach { (type, label) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                                palette.forEach { hex ->
                                    Box(
                                        Modifier.size(25.dp)
                                            .background(Color(android.graphics.Color.parseColor(hex)), RoundedCornerShape(7.dp))
                                            .then(if ((cellColors[type] ?: "") == hex) Modifier.border(2.dp, Red, RoundedCornerShape(7.dp)) else Modifier)
                                            .clickable {
                                                cellColors[type] = hex
                                                save()
                                            }
                                    )
                                }
                            }
                        }
                    }
                    Text("Выбранные цвета сохраняются и сразу применяются к ячейкам.", color = Muted, fontSize = 10.sp)
                }
            },
            confirmButton = { TextButton(onClick = { colorsDialog = false }) { Text("Готово", color = Red) } },
            dismissButton = {
                TextButton(onClick = {
                    cellColors[0] = "#F0F0F2"; cellColors[1] = "#FFC94A"; cellColors[2] = "#2384F5"
                    cellColors[3] = "#FFE1E1"; cellColors[4] = "#DDF3E4"
                    save()
                }) { Text("Сбросить") }
            }
        )
    }

    if (addDialog) {
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("Новый сотрудник") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Имя сотрудника") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        names.add(newName.trim())
                        employeeIds.add("emp_" + System.currentTimeMillis() + "_" + employeeIds.size)
                        save()
                        newName = ""
                    }
                    addDialog = false
                }) { Text("Добавить", color = Red) }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Отмена") } }
        )
    }

    deleteIndex?.let { index ->
        val employeeName = names.getOrNull(index).orEmpty()
        AlertDialog(
            onDismissRequest = { deleteIndex = null },
            title = { Text("Удалить сотрудника?") },
            text = { Text("«" + employeeName + "» и его отметки в графике будут удалены без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = employeeIds.getOrNull(index)
                    if (id != null) {
                        val prefix = id + "-"
                        cellTypes.keys.filter { it.startsWith(prefix) }.forEach { cellTypes.remove(it) }
                        cellText.keys.filter { it.startsWith(prefix) }.forEach { cellText.remove(it) }
                        cellStarts.keys.filter { it.startsWith(prefix) }.forEach { cellStarts.remove(it) }
                        cellEnds.keys.filter { it.startsWith(prefix) }.forEach { cellEnds.remove(it) }
                        names.removeAt(index)
                        employeeIds.removeAt(index)
                        save()
                    }
                    deleteIndex = null
                }) { Text("Удалить", color = Red) }
            },
            dismissButton = { TextButton(onClick = { deleteIndex = null }) { Text("Отмена") } }
        )
    }
    selectedCell?.let { key ->
        AlertDialog(
            onDismissRequest = { selectedCell = null },
            title = { Text("Отметка дня") },
            text = { Text("Выберите время начала или окончания смены либо отметьте отсутствие.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { timePickerKind = "С"; timePickerCell = key; selectedCell = null }) { Text("С") }
                    TextButton(onClick = { timePickerKind = "ДО"; timePickerCell = key; selectedCell = null }) { Text("ДО") }
                    TextButton(onClick = {
                        cellTypes[key] = 3
                        cellText.remove(key); cellStarts.remove(key); cellEnds.remove(key)
                        save(); selectedCell = null
                    }) { Text("БС", color = SickText) }
                    TextButton(onClick = {
                        cellTypes[key] = 4
                        cellText.remove(key); cellStarts.remove(key); cellEnds.remove(key)
                        save(); selectedCell = null
                    }) { Text("ОТ", color = VacationText) }
                    TextButton(onClick = {
                        cellTypes.remove(key); cellText.remove(key); cellStarts.remove(key); cellEnds.remove(key)
                        save(); selectedCell = null
                    }) { Text("Очистить", color = Red) }
                }
            },
            dismissButton = { TextButton(onClick = { selectedCell = null }) { Text("Отмена") } }
        )
    }
    timePickerCell?.let { key ->
        val options = (0..47).map { String.format(Locale.ROOT, "%02d:%02d", it / 2, if (it % 2 == 0) 0 else 30) }
        AlertDialog(
            onDismissRequest = { timePickerCell = null },
            title = { Text(if (timePickerKind == "С") "Время начала смены" else "Время окончания смены") },
            text = {
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    options.chunked(4).forEach { group ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            group.forEach { time ->
                                TextButton(
                                    onClick = {
                                        if (timePickerKind == "С") cellStarts[key] = time else cellEnds[key] = time
                                        if ((cellTypes[key] ?: 0) !in 3..4) cellTypes[key] = 1
                                        cellText.remove(key)
                                        save()
                                        timePickerCell = null
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(time, fontSize = 12.sp) }
                            }
                            repeat(4 - group.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { timePickerCell = null }) { Text("Закрыть") } }
        )
    }
    if (totalsDialog) {
        fun creditedMinutes(employeeId: String): Int {
            var total = 0
            for (day in 1..month.lengthOfMonth()) {
                val key = "$employeeId-${month.year}-${month.monthValue}-$day"
                val start = cellStarts[key] ?: continue
                val end = cellEnds[key] ?: continue
                val startParts = start.split(":")
                val endParts = end.split(":")
                val startMin = startParts[0].toInt() * 60 + startParts[1].toInt()
                val endMin = endParts[0].toInt() * 60 + endParts[1].toInt()
                val elapsed = if (endMin > startMin) endMin - startMin else endMin + 1440 - startMin
                total += (elapsed - 60).coerceAtLeast(0)
            }
            return total
        }
        val teamMinutes = employeeIds.sumOf { creditedMinutes(it) }
        AlertDialog(
            onDismissRequest = { totalsDialog = false },
            title = { Text("Итоги за месяц") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Всего по команде: ${teamMinutes / 60} ч ${teamMinutes % 60} мин",
                        fontWeight = FontWeight.Bold, color = Red)
                    Divider()
                    names.forEachIndexed { index, name ->
                        val minutes = creditedMinutes(employeeIds[index])
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, modifier = Modifier.weight(1f), fontSize = 12.sp)
                            Text("${minutes / 60} ч ${minutes % 60} мин",
                                fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    Text("Из каждой смены автоматически вычтен 1 час на обед. Неполные отметки времени не учитываются.",
                        color = Muted, fontSize = 10.sp)
                }
            },
            confirmButton = { TextButton(onClick = { totalsDialog = false }) { Text("Готово") } }
        )
    }
    editCaptionCell?.let { key ->
        AlertDialog(
            onDismissRequest = { editCaptionCell = null },
            title = { Text("Подпись смены") },
            text = { OutlinedTextField(value = editText, onValueChange = { editText = it }, label = { Text("Например: 08:00–20:00") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (editText.isBlank()) cellText.remove(key) else cellText[key] = editText
                    if ((cellTypes[key] ?: 0) == 0) cellTypes[key] = employeeModes[key.substringBefore("-")] ?: 1
                    save()
                    editCaptionCell = null
                }) { Text("Сохранить", color = Red) }
            },
            dismissButton = { TextButton(onClick = { editCaptionCell = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
        Text(label, fontSize = 9.sp, color = Muted)
    }
}

private fun xmlEscape(value: String): String = value
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    .replace("\"", "&quot;").replace("'", "&apos;")

private fun excelColumn(index: Int): String {
    var n = index
    val result = StringBuilder()
    while (n > 0) {
        val r = (n - 1) % 26
        result.insert(0, ('A'.code + r).toChar())
        n = (n - 1) / 26
    }
    return result.toString()
}

private fun writeScheduleXlsx(
    output: OutputStream,
    names: List<String>,
    employeeIds: List<String>,
    month: YearMonth,
    cellTypes: Map<String, Int>,
    cellText: Map<String, String>,
    cellStarts: Map<String, String>,
    cellEnds: Map<String, String>
) {
    val sheet = StringBuilder()
    val lastCol = excelColumn(month.lengthOfMonth() + 1)
    sheet.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<dimension ref="A1:${lastCol}${names.size + 1}"/>
<sheetViews><sheetView workbookViewId="0"><pane xSplit="1" ySplit="1" topLeftCell="B2" activePane="bottomRight" state="frozen"/></sheetView></sheetViews>
<cols><col min="1" max="1" width="24" customWidth="1"/><col min="2" max="${month.lengthOfMonth() + 1}" width="8" customWidth="1"/></cols>
<sheetData>""")
    sheet.append("<row r=\"1\" ht=\"32\" customHeight=\"1\">")
    sheet.append("""<c r="A1" s="1" t="inlineStr"><is><t>СОТРУДНИК</t></is></c>""")
    for (day in 1..month.lengthOfMonth()) {
        val date = month.atDay(day)
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru"))
            .take(2).uppercase(Locale("ru"))
        val ref = "${excelColumn(day + 1)}1"
        sheet.append("<c r=\"$ref\" s=\"1\" t=\"inlineStr\"><is><t>${day.toString().padStart(2, '0')} $weekday</t></is></c>")
    }
    sheet.append("</row>")
    names.forEachIndexed { index, name ->
        val row = index + 2
        sheet.append("<row r=\"$row\" ht=\"25\" customHeight=\"1\">")
        sheet.append("<c r=\"A$row\" s=\"3\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xmlEscape(name)}</t></is></c>")
        for (day in 1..month.lengthOfMonth()) {
            val key = employeeIds.getOrNull(index).orEmpty() + "-${month.year}-${month.monthValue}-$day"
            val type = cellTypes[key] ?: 0
            if (type in 1..4) {
                val label = when (type) {
                    1, 2 -> {
                        val start = cellStarts[key]
                        val end = cellEnds[key]
                        when {
                            start != null && end != null -> "С $start / ДО $end"
                            start != null -> "С $start"
                            end != null -> "ДО $end"
                            else -> cellText[key].orEmpty().ifBlank { "СМЕНА" }
                        }
                    }
                    3 -> "БС"
                    else -> "ОТ"
                }
                val ref = "${excelColumn(day + 1)}$row"
                val style = when (type) { 1 -> 3; 2 -> 4; 3 -> 5; else -> 6 }
                sheet.append("<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t>${xmlEscape(label)}</t></is></c>")
            }
        }
        sheet.append("</row>")
    }
    sheet.append("</sheetData><autoFilter ref=\"A1:${lastCol}${names.size + 1}\"/></worksheet>")

    val styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="3"><font><sz val="11"/><name val="Calibri"/></font><font><b/><color rgb="FFFFFFFF"/><sz val="10"/><name val="Calibri"/></font><font><b/><sz val="10"/><name val="Calibri"/></font></fonts>
<fills count="7"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFE31E24"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFFC94A"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FF2384F5"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFFE1E1"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFDDF3E4"/><bgColor indexed="64"/></patternFill></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="7"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="2" fillId="0" borderId="0" applyAlignment="1"><alignment vertical="center"/></xf><xf numFmtId="0" fontId="2" fillId="3" borderId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="1" fillId="4" borderId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="2" fillId="5" borderId="0" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf><xf numFmtId="0" fontId="2" fillId="6" borderId="0" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf></cellXfs>
</styleSheet>"""

    ZipOutputStream(output).use { zip ->
        fun entry(path: String, content: String) {
            zip.putNextEntry(ZipEntry(path))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""")
        entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""")
        entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="График" sheetId="1" r:id="rId1"/></sheets></workbook>""")
        entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""")
        entry("xl/styles.xml", styles)
        entry("xl/worksheets/sheet1.xml", sheet.toString())
    }
}
