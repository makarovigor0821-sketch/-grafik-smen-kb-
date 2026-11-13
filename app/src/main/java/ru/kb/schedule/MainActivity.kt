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
    var month by remember { mutableStateOf(YearMonth.now()) }
    var addDialog by remember { mutableStateOf(false) }
    var deleteIndex by remember { mutableStateOf<Int?>(null) }
    var newName by remember { mutableStateOf("") }
    val employeeScroll = rememberScrollState()
    var selectedCell by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                val output = context.contentResolver.openOutputStream(uri) ?: error("Не удалось создать файл")
                output.use {
                    writeScheduleXlsx(it, names.toList(), employeeIds.toList(), month, cellTypes.toMap(), cellText.toMap())
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
        prefs.edit().putString("employees", employeeJson.toString())
            .putString("employee_ids", idsJson.toString())
            .putString("texts", textsJson.toString()).putString("types", typesJson.toString())
            .putString("employee_modes", modesJson.toString()).apply()
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
                                                .background(if (mode == 1) EarlyShift else LateShift, RoundedCornerShape(6.dp))
                                                .clickable { employeeModes[employeeId] = if (mode == 1) 2 else 1; save() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(if (mode == 1) "До 16:00" else "С 16:00", color = if (mode == 1) Ink else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                                        Box(
                                            Modifier.weight(1f).fillMaxHeight().padding(1.dp)
                                                .background(if (!isShift) Off else if (shiftType == 2) LateShift else EarlyShift, RoundedCornerShape(4.dp))
                                                .combinedClickable(
                                                    onClick = {
                                                        if (isShift) {
                                                            cellTypes.remove(key)
                                                            cellText.remove(key)
                                                        } else {
                                                            cellTypes[key] = employeeModes[employeeIds[index]] ?: 1
                                                            cellText.remove(key)
                                                        }
                                                        save()
                                                    },
                                                    onLongClick = {
                                                        selectedCell = key
                                                        editText = label
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isShift && label.isNotBlank()) Text(label, color = if (shiftType == 2) Color.White else Ink, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(22.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(EarlyShift, "До 16:00")
                    LegendDot(LateShift, "С 16:00")
                    LegendDot(Off, "Выходной")
                    Text("Кнопка у имени задаёт тип новой смены", fontSize = 9.sp, color = Muted)
                }
            }
        }
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
            title = { Text("Подпись смены") },
            text = { OutlinedTextField(value = editText, onValueChange = { editText = it }, label = { Text("Например: 08:00–20:00") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (editText.isBlank()) cellText.remove(key) else cellText[key] = editText
                    if ((cellTypes[key] ?: 0) == 0) cellTypes[key] = employeeModes[key.substringBefore("-")] ?: 1
                    save()
                    selectedCell = null
                }) { Text("Сохранить", color = Red) }
            },
            dismissButton = { TextButton(onClick = { selectedCell = null }) { Text("Отмена") } }
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
    cellText: Map<String, String>
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
            if ((cellTypes[key] ?: 0) == 1 || (cellTypes[key] ?: 0) == 2) {
                val label = cellText[key].orEmpty().ifBlank { if ((cellTypes[key] ?: 1) == 2) "С 16:00" else "До 16:00" }
                val ref = "${excelColumn(day + 1)}$row"
                val style = if ((cellTypes[key] ?: 1) == 2) 4 else 3
                sheet.append("<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t>${xmlEscape(label)}</t></is></c>")
            }
        }
        sheet.append("</row>")
    }
    sheet.append("</sheetData><autoFilter ref=\"A1:${lastCol}${names.size + 1}\"/></worksheet>")

    val styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="3"><font><sz val="11"/><name val="Calibri"/></font><font><b/><color rgb="FFFFFFFF"/><sz val="10"/><name val="Calibri"/></font><font><b/><sz val="10"/><name val="Calibri"/></font></fonts>
<fills count="5"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFE31E24"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFFC94A"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FF2384F5"/><bgColor indexed="64"/></patternFill></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="5"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="2" fillId="0" borderId="0" applyAlignment="1"><alignment vertical="center"/></xf><xf numFmtId="0" fontId="2" fillId="3" borderId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="1" fillId="4" borderId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf></cellXfs>
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
