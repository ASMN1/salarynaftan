package com.example.salarynaftan

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings = SettingsManager(this)

            var isDarkTheme by remember {
                mutableStateOf(settings.isDarkTheme())
            }

            MaterialTheme(
                colorScheme = if (isDarkTheme) darkColorScheme(
                    primary = Color(0xFF00E676),
                    surface = Color(0xFF1E1E1E),
                    background = Color(0xFF121212),
                    onSurface = Color.White,
                    onBackground = Color.White
                ) else lightColorScheme(
                    primary = Color(0xFF00A859),
                    surface = Color(0xFFF5F5F5),
                    background = Color.White,
                    onSurface = Color.Black,
                    onBackground = Color.Black
                )
            ) {
                MainAppScreen(isDarkTheme = isDarkTheme, onThemeChange = { isDarkTheme = it })
            }
        }
    }
}

@Composable
fun MainAppScreen(isDarkTheme: Boolean, onThemeChange: (Boolean) -> Unit) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("📅", fontSize = 18.sp) },
                    label = { Text("График смен", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("💰", fontSize = 18.sp) },
                    label = { Text("Зарплата", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Text("⚙️", fontSize = 18.sp) },
                    label = { Text("Настройки", fontSize = 11.sp) }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when(selectedTab){

                0 -> ScheduleScreen(
                    isDarkTheme = isDarkTheme,
                    onThemeChange = onThemeChange
                )


                1 -> SalaryCalculatorScreen(
                    isDarkTheme = isDarkTheme
                )


                2 -> SettingsScreen(
                    isDarkTheme = isDarkTheme,
                    onThemeChange = onThemeChange
                )

            }
        }
    }
}

// ==========================================
// ЭКРАН: ЗАРПЛАТА
// ==========================================
@Composable
fun SalaryCalculatorScreen(isDarkTheme: Boolean) {
    val context = LocalContext.current
    val historyManager = remember { HistoryManager(context) }
    var historyList by remember { mutableStateOf(historyManager.getRecords()) }

    val months = remember {
        listOf(
            MonthData("Январь", 132.0, 144.0, 0.0, 0.0, 9.0),
            MonthData("Февраль", 140.0, 136.0, 0.0, 0.0, 8.0),
            MonthData("Март", 154.0, 152.0, 0.0, 0.0, 10.0),
            MonthData("Апрель", 145.0, 144.0, 0.0, 0.0, 9.0),
            MonthData("Май", 139.0, 144.0, 0.0, 0.0, 9.0),
            MonthData("Июнь", 154.0, 144.0, 6.0, 6.0, 9.0),
            MonthData("Июль", 153.0, 144.0, 6.0, 6.0, 9.0),
            MonthData("Август", 147.0, 151.5, 6.0, 7.0, 10.0),
            MonthData("Сентябрь", 154.0, 144.0, 6.0, 6.0, 9.0),
            MonthData("Октябрь", 154.0, 151.5, 6.0, 7.0, 9.0),
            MonthData("Ноябрь", 146.0, 144.0, 6.0, 6.0, 9.0),
            MonthData("Декабрь", 152.0, 144.0, 6.0, 6.0, 9.0)
        )
    }

    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedMonthIndex by rememberSaveable { mutableStateOf(5) }
    val currentMonth = months[selectedMonthIndex]

    val prefs = remember { context.getSharedPreferences("salary_months_data", Context.MODE_PRIVATE) }

    val okladBase = 1607.93
    val koefStazh = 0.25
    val vrednostKoef = 0.423125
    val koefNoch = 0.4
    val koefPrem = 0.45
    val vychetNaOdnogoRebenka = 63.0
    val bazovayaVelichina = 45.0

    var normHours by remember { mutableStateOf(currentMonth.norm.toString()) }
    var factHours by remember { mutableStateOf(currentMonth.fact.toString()) }
    var childrenCountInput by remember { mutableStateOf("2") }

    var nightShifts by remember { mutableStateOf(currentMonth.defaultNightShifts.toString()) }
    var s4Shifts by remember { mutableStateOf(currentMonth.defaultS4Shifts.toString()) }
    var prazdnHours by remember { mutableStateOf("0") }
    var advanceShifts by remember { mutableStateOf(currentMonth.defaultAdvanceShifts.toString()) }

    var zaOtsutstvuushego by remember { mutableStateOf("") }
    var kvartalka by remember { mutableStateOf("") }
    var gazetaInput by remember { mutableStateOf("0") }
    var pozhertvovanjaInput by remember { mutableStateOf("0") }
    var subbotnikInput by remember { mutableStateOf("0") }
    var zaSvoySchetInput by remember { mutableStateOf("0") }
    var mmDetiCountInput by remember { mutableStateOf("0") }

    fun saveCurrentMonthData() {
        prefs.edit()
            .putString("norm_$selectedMonthIndex", normHours)
            .putString("fact_$selectedMonthIndex", factHours)
            .putString("night_$selectedMonthIndex", nightShifts)
            .putString("s4_$selectedMonthIndex", s4Shifts)
            .putString("adv_$selectedMonthIndex", advanceShifts)
            .putString("prazdn_$selectedMonthIndex", prazdnHours)
            .putString("otsut_$selectedMonthIndex", zaOtsutstvuushego)
            .putString("kvart_$selectedMonthIndex", kvartalka)
            .putString("gaz_$selectedMonthIndex", gazetaInput)
            .putString("poz_$selectedMonthIndex", pozhertvovanjaInput)
            .putString("sub_$selectedMonthIndex", subbotnikInput)
            .putString("svoy_$selectedMonthIndex", zaSvoySchetInput)
            .putString("mmdeti_$selectedMonthIndex", mmDetiCountInput)
            .putString("children_$selectedMonthIndex", childrenCountInput)
            .apply()
    }

    LaunchedEffect(selectedMonthIndex) {
        val m = months[selectedMonthIndex]
        normHours = prefs.getString("norm_$selectedMonthIndex", m.norm.toString()) ?: m.norm.toString()
        factHours = prefs.getString("fact_$selectedMonthIndex", m.fact.toString()) ?: m.fact.toString()
        nightShifts = prefs.getString("night_$selectedMonthIndex", m.defaultNightShifts.toString()) ?: m.defaultNightShifts.toString()
        s4Shifts = prefs.getString("s4_$selectedMonthIndex", m.defaultS4Shifts.toString()) ?: m.defaultS4Shifts.toString()
        advanceShifts = prefs.getString("adv_$selectedMonthIndex", m.defaultAdvanceShifts.toString()) ?: m.defaultAdvanceShifts.toString()

        prazdnHours = prefs.getString("prazdn_$selectedMonthIndex", "0") ?: "0"
        zaOtsutstvuushego = prefs.getString("otsut_$selectedMonthIndex", "") ?: ""
        kvartalka = prefs.getString("kvart_$selectedMonthIndex", "") ?: ""
        gazetaInput = prefs.getString("gaz_$selectedMonthIndex", "0") ?: "0"
        pozhertvovanjaInput = prefs.getString("poz_$selectedMonthIndex", "0") ?: "0"
        subbotnikInput = prefs.getString("sub_$selectedMonthIndex", "0") ?: "0"
        zaSvoySchetInput = prefs.getString("svoy_$selectedMonthIndex", "0") ?: "0"
        mmDetiCountInput = prefs.getString("mmdeti_$selectedMonthIndex", "0") ?: "0"
        childrenCountInput = prefs.getString("children_$selectedMonthIndex", "2") ?: "2"
    }

    var showResults by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    var resSumBeforePension by rememberSaveable { mutableStateOf(0.0) }
    var resOkladReal by rememberSaveable { mutableStateOf(0.0) }
    var resStazh by rememberSaveable { mutableStateOf(0.0) }
    var resVrednost by rememberSaveable { mutableStateOf(0.0) }
    var resNightHours by rememberSaveable { mutableStateOf(0.0) }
    var resNochPay by rememberSaveable { mutableStateOf(0.0) }
    var resPrazdn by rememberSaveable { mutableStateOf(0.0) }
    var resPrem by rememberSaveable { mutableStateOf(0.0) }
    var resMmDeti by rememberSaveable { mutableStateOf(0.0) }
    var resPension by rememberSaveable { mutableStateOf(0.0) }
    var resDirty by rememberSaveable { mutableStateOf(0.0) }
    var resFszn by rememberSaveable { mutableStateOf(0.0) }
    var resProf by rememberSaveable { mutableStateOf(0.0) }
    var resChildrenDeduction by rememberSaveable { mutableStateOf(0.0) }
    var resPodohodnyBase by rememberSaveable { mutableStateOf(0.0) }
    var resPodohodny by rememberSaveable { mutableStateOf(0.0) }
    var resAvans by rememberSaveable { mutableStateOf(0.0) }
    var resTotalClean by rememberSaveable { mutableStateOf(0.0) }
    var resCleanToPay by rememberSaveable { mutableStateOf(0.0) }
    var effectiveFactText by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "НАФТАН ЗАРПЛАТА", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676), modifier = Modifier.padding(vertical = 4.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = months[selectedMonthIndex].name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Месяц расчета") },
                trailingIcon = { Text("▼", color = Color(0xFF00E676), modifier = Modifier.padding(end = 4.dp)) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 14.sp)
            )
            Box(modifier = Modifier.matchParentSize().clickable { expanded = true })
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth(0.9f)) {
                months.forEachIndexed { index, monthData ->
                    DropdownMenuItem(text = { Text(monthData.name) }, onClick = { selectedMonthIndex = index; expanded = false; showResults = false; errorMessage = null })
                }
            }
        }

        CompactInputCard(title = "Рабочее время и дети") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactTextField(value = normHours, onValueChange = { normHours = it; saveCurrentMonthData() }, label = "Норма часов", modifier = Modifier.weight(1f))
                CompactTextField(value = factHours, onValueChange = { factHours = it; saveCurrentMonthData() }, label = "Факт часов", modifier = Modifier.weight(1f))
                CompactTextField(value = childrenCountInput, onValueChange = { childrenCountInput = it; saveCurrentMonthData() }, label = "Кол-во детей", modifier = Modifier.weight(1f))
            }
        }

        CompactInputCard(title = "Смены и доплаты за график") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactTextField(value = nightShifts, onValueChange = { nightShifts = it; saveCurrentMonthData() }, label = "Ночные смены", modifier = Modifier.weight(1f))
                CompactTextField(value = s4Shifts, onValueChange = { s4Shifts = it; saveCurrentMonthData() }, label = "Смены \"с 4\"", modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                CompactTextField(value = prazdnHours, onValueChange = { prazdnHours = it; saveCurrentMonthData() }, label = "Праздн. часы", modifier = Modifier.weight(1f))
                CompactTextField(value = advanceShifts, onValueChange = { advanceShifts = it; saveCurrentMonthData() }, label = "Смен аванса (1-15)", modifier = Modifier.weight(1f))
            }
        }

        CompactInputCard(title = "Премии и дополнительные выплаты") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactTextField(value = zaOtsutstvuushego, onValueChange = { zaOtsutstvuushego = it; saveCurrentMonthData() }, label = "За отсутств. (руб)", modifier = Modifier.weight(1f))
                CompactTextField(value = kvartalka, onValueChange = { kvartalka = it; saveCurrentMonthData() }, label = "Кварталка (руб)", modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                CompactTextField(value = mmDetiCountInput, onValueChange = { mmDetiCountInput = it; saveCurrentMonthData() }, label = "МП на детей до 3л (баз.вел.)", modifier = Modifier.weight(1f))
            }
        }

        CompactInputCard(title = "Удержания и невыходы", isDanger = true) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactTextField(value = gazetaInput, onValueChange = { gazetaInput = it; saveCurrentMonthData() }, label = "Газета (руб)", modifier = Modifier.weight(1f))
                CompactTextField(value = pozhertvovanjaInput, onValueChange = { pozhertvovanjaInput = it; saveCurrentMonthData() }, label = "Пожертв. (руб)", modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                CompactTextField(value = subbotnikInput, onValueChange = { subbotnikInput = it; saveCurrentMonthData() }, label = "Субботник (руб)", modifier = Modifier.weight(1f))
                CompactTextField(value = zaSvoySchetInput, onValueChange = { zaSvoySchetInput = it; saveCurrentMonthData() }, label = "За свой счет (смен)", modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (errorMessage != null) {
            Text(text = errorMessage ?: "", color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        }

        Button(
            onClick = {
                val normVal = parseNonNegative(normHours)
                val factVal = parseNonNegative(factHours)
                val nShiftsVal = parseNonNegative(nightShifts)
                val s4ShiftsVal = parseNonNegative(s4Shifts)
                val prazdnVal = parseNonNegative(prazdnHours)
                val advShiftsVal = parseNonNegative(advanceShifts)
                val vOtsut = parseNonNegative(zaOtsutstvuushego)
                val vKvartal = parseNonNegative(kvartalka)
                val vGaz = parseNonNegative(gazetaInput)
                val vPoz = parseNonNegative(pozhertvovanjaInput)
                val vSub = parseNonNegative(subbotnikInput)
                val vZaSvoyShifts = parseNonNegative(zaSvoySchetInput)
                val childrenCount = parseNonNegative(childrenCountInput)
                val mmDetiCountVal = parseNonNegative(mmDetiCountInput)

                if (normVal <= 0.0) { errorMessage = "Норма часов должна быть больше нуля"; showResults = false; return@Button }
                errorMessage = null

                val hoursZaSvoy = vZaSvoyShifts * 8.0
                val effectiveFactHours = maxOf(0.0, factVal - hoursZaSvoy)
                effectiveFactText = if (vZaSvoyShifts > 0) " (-${hoursZaSvoy.toInt()} ч за свой счет)" else ""

                resOkladReal = (okladBase / normVal) * effectiveFactHours
                resStazh = resOkladReal * koefStazh
                resVrednost = vrednostKoef * effectiveFactHours
                resNightHours = (nShiftsVal * 8.0) + (s4ShiftsVal * 2.0)
                resNochPay = (okladBase / normVal) * resNightHours * koefNoch
                resPrazdn = (okladBase / normVal) * prazdnVal

                val prevMonthIndex = (selectedMonthIndex - 1 + 12) % 12
                resPrem = (okladBase / months[prevMonthIndex].norm) * months[prevMonthIndex].fact * koefPrem
                resMmDeti = mmDetiCountVal * bazovayaVelichina

                resSumBeforePension = resOkladReal + resStazh + resVrednost + resNochPay + resPrazdn + resPrem + vOtsut + vKvartal
                resPension = resSumBeforePension * 0.06
                resDirty = resSumBeforePension + resPension + resMmDeti
                resFszn = resDirty * 0.01
                resProf = resDirty * 0.01
                resChildrenDeduction = vychetNaOdnogoRebenka * childrenCount
                resPodohodnyBase = maxOf(0.0, resDirty - resChildrenDeduction - resMmDeti)
                resPodohodny = resPodohodnyBase * 0.13
                resAvans = (okladBase / normVal) * advShiftsVal * 8.0
                resTotalClean = resDirty - resFszn - resProf - resPodohodny - vGaz - vPoz - vSub
                resCleanToPay = resTotalClean - resAvans
                showResults = true
            },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("РАССЧИТАТЬ", color = if (isDarkTheme) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        if (showResults) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailedSectionTitle(title = "1. ВЫПЛАТЫ И ИТОГИ")
                    ResultRowDetailed("Всего чистыми за месяц:", "${String.format(Locale.US, "%.2f", resTotalClean)} руб", isBold = true, isDark = isDarkTheme)
                    ResultRowDetailed("Выплачено в аванс:", "-${String.format(Locale.US, "%.2f", resAvans)} руб", isBold = true, isDark = isDarkTheme)

                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("К ВЫПЛАТЕ В ДЕНЬ ЗП:", color = Color(0xFF00E676), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Text("${String.format(Locale.US, "%.2f", resCleanToPay)} руб", color = Color(0xFF00E676), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }

                    HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
                    DetailedSectionTitle(title = "2. СПРАВОЧНО (ВРЕМЯ)")
                    ResultRowDetailed("Норма рабочего времени:", "${displayInt(normHours)} ч", isDark = isDarkTheme)
                    ResultRowDetailed("Фактически отработано$effectiveFactText:", "${displayInt(factHours)} ч", isDark = isDarkTheme)
                    ResultRowDetailed("Из них ночного времени:", "${resNightHours.toInt()} ч", isDark = isDarkTheme)

                    HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
                    DetailedSectionTitle(title = "3. НАЧИСЛЕНО (ГРЯЗНЫМИ)")
                    ResultRowDetailed("Оклад расчетный:", "${String.format(Locale.US, "%.2f", resOkladReal)} руб", isDark = isDarkTheme)
                    ResultRowDetailed("Надбавка за стаж (25%):", "${String.format(Locale.US, "%.2f", resStazh)} руб", isDark = isDarkTheme)
                    ResultRowDetailed("Доплата за вредность:", "${String.format(Locale.US, "%.2f", resVrednost)} руб", isDark = isDarkTheme)
                    ResultRowDetailed("Доплата за ночные часы:", "${String.format(Locale.US, "%.2f", resNochPay)} руб", isDark = isDarkTheme)
                    if (resPrazdn > 0) ResultRowDetailed("Праздничные часы:", "${String.format(Locale.US, "%.2f", resPrazdn)} руб", isDark = isDarkTheme)
                    ResultRowDetailed("Премия (за прошлый мес.):", "${String.format(Locale.US, "%.2f", resPrem)} руб", isDark = isDarkTheme)

                    if (parseNonNegative(zaOtsutstvuushego) > 0) ResultRowDetailed("За отсутствующего сотрудника:", "${String.format(Locale.US, "%.2f", parseNonNegative(zaOtsutstvuushego))} руб", isDark = isDarkTheme)
                    if (parseNonNegative(kvartalka) > 0) ResultRowDetailed("Квартальная премия:", "${String.format(Locale.US, "%.2f", parseNonNegative(kvartalka))} руб", isDark = isDarkTheme)
                    if (resMmDeti > 0) ResultRowDetailed("МП на детей до 3л (${displayInt(mmDetiCountInput)} баз.вел.):", "${String.format(Locale.US, "%.2f", resMmDeti)} руб", isDark = isDarkTheme)
                    ResultRowDetailed("За профессиональную пенсию (ППС 6%):", "${String.format(Locale.US, "%.2f", resPension)} руб", isDark = isDarkTheme)

                    Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ВСЕГО НАЧИСЛЕНО:", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("${String.format(Locale.US, "%.2f", resDirty)} руб", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
                    DetailedSectionTitle(title = "4. НАЛОГОВАЯ БАЗА")
                    ResultRowDetailed("Общая сумма начислений:", "${String.format(Locale.US, "%.2f", resDirty)} руб", isDark = isDarkTheme)
                    ResultRowDetailed("Налоговый вычет на детей (${displayInt(childrenCountInput)} дет.):", "-${String.format(Locale.US, "%.2f", resChildrenDeduction)} руб", isGray = true, isDark = isDarkTheme)
                    if (resMmDeti > 0) ResultRowDetailed("МП на детей (не облагается):", "-${String.format(Locale.US, "%.2f", resMmDeti)} руб", isGray = true, isDark = isDarkTheme)
                    ResultRowDetailed("Облагаемый подоходным доходом:", "${String.format(Locale.US, "%.2f", resPodohodnyBase)} руб", isGray = true, isDark = isDarkTheme)

                    HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
                    DetailedSectionTitle(title = "5. УДЕРЖАНО", isDanger = true)
                    ResultRowDetailed("Пенсионный фонд (1% ФСЗН):", "${String.format(Locale.US, "%.2f", resFszn)} руб", isDark = isDarkTheme)
                    ResultRowDetailed("Профсоюзный взнос (1%):", "${String.format(Locale.US, "%.2f", resProf)} руб", isDark = isDarkTheme)
                    ResultRowDetailed("Подоходный налог (13%):", "${String.format(Locale.US, "%.2f", resPodohodny)} руб", isDark = isDarkTheme)

                    if (parseNonNegative(gazetaInput) > 0) ResultRowDetailed("Удержание (Газета):", "${String.format(Locale.US, "%.2f", parseNonNegative(gazetaInput))} руб", isDark = isDarkTheme)
                    if (parseNonNegative(pozhertvovanjaInput) > 0) ResultRowDetailed("Удержание (Пожертвования):", "${String.format(Locale.US, "%.2f", parseNonNegative(pozhertvovanjaInput))} руб", isDark = isDarkTheme)
                    if (parseNonNegative(subbotnikInput) > 0) ResultRowDetailed("Удержание (Субботник):", "${String.format(Locale.US, "%.2f", parseNonNegative(subbotnikInput))} руб", isDark = isDarkTheme)

                    val totalUderzhano = resFszn + resProf + resPodohodny + parseNonNegative(gazetaInput) + parseNonNegative(pozhertvovanjaInput) + parseNonNegative(subbotnikInput)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ВСЕГО УДЕРЖАНО:", color = Color.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("${String.format(Locale.US, "%.2f", totalUderzhano)} руб", color = Color.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            historyManager.saveRecord(selectedMonthIndex, months[selectedMonthIndex].name, resTotalClean, resCleanToPay, resAvans)
                            historyList = historyManager.getRecords()
                        },
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x3300E676))
                    ) {
                        Text("СОХРАНИТЬ РАСЧЕТ В ИСТОРИЮ", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, Color.DarkGray)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "ИСТОРИЯ АРХИВНЫХ ВЫПЛАТ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                if (historyList.isEmpty()) {
                    Text(text = "История пока пуста. Рассчитайте и сохраните выплаты.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
                } else {
                    historyList.forEach { record ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isDarkTheme) Color(0xFF252525) else Color(0xFFEEEEEE), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(record.monthName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Ав: ${String.format(Locale.US, "%.1f", record.advance)} р.", fontSize = 11.sp, color = Color.Gray)
                                    Text("На руки: ${String.format(Locale.US, "%.1f", record.cleanToPay)} р.", fontSize = 11.sp, color = Color(0xFF00E676))
                                    Text("Итого: ${String.format(Locale.US, "%.1f", record.totalClean)} р.", fontSize = 11.sp, color = if (isDarkTheme) Color.White else Color.Black, fontWeight = FontWeight.Medium)
                                }
                            }
                            Text(
                                text = "✕",
                                color = Color.Red,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .clickable {
                                        historyManager.deleteRecord(record.monthIndex)
                                        historyList = historyManager.getRecords()
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// ВСПОМОГАТЕЛЬНЫЕ КЛАССЫ И КОМПОНЕНТЫ ЗАРПЛАТЫ
// ==========================================
data class SalaryHistoryRecord(
    val monthIndex: Int,
    val monthName: String,
    val totalClean: Double,
    val cleanToPay: Double,
    val advance: Double
)

class HistoryManager(context: Context) {
    private val prefs = context.getSharedPreferences("salary_history_prefs", Context.MODE_PRIVATE)

    fun getRecords(): List<SalaryHistoryRecord> {
        val raw = prefs.getString("history_records", "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size == 5) {
                val idx = parts[0].toIntOrNull() ?: return@mapNotNull null
                val name = parts[1]
                val total = parts[2].toDoubleOrNull() ?: return@mapNotNull null
                val clean = parts[3].toDoubleOrNull() ?: return@mapNotNull null
                val adv = parts[4].toDoubleOrNull() ?: return@mapNotNull null
                SalaryHistoryRecord(idx, name, total, clean, adv)
            } else null
        }
    }

    fun saveRecord(monthIndex: Int, monthName: String, totalClean: Double, cleanToPay: Double, advance: Double) {
        val list = getRecords().toMutableList()
        list.removeAll { it.monthIndex == monthIndex }
        list.add(0, SalaryHistoryRecord(monthIndex, monthName, totalClean, cleanToPay, advance))
        saveList(list)
    }

    fun deleteRecord(monthIndex: Int) {
        val list = getRecords().toMutableList()
        list.removeAll { it.monthIndex == monthIndex }
        saveList(list)
    }

    private fun saveList(list: List<SalaryHistoryRecord>) {
        val raw = list.joinToString(";") { "${it.monthIndex}|${it.monthName}|${it.totalClean}|${it.cleanToPay}|${it.advance}" }
        prefs.edit().putString("history_records", raw).apply()
    }
}

@Composable
fun CompactInputCard(title: String, isDanger: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isDanger) Color(0xAAFF5252) else Color.DarkGray)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isDanger) Color(0xFFFF5252) else Color(0xFF00E676), modifier = Modifier.padding(bottom = 6.dp))
            content()
        }
    }
}

@Composable
fun CompactTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 10.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        textStyle = TextStyle(fontSize = 13.sp)
    )
}

@Composable
fun DetailedSectionTitle(title: String, isDanger: Boolean = false) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = if (isDanger) Color(0xFFFF5252) else Color(0xFF00E676),
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
fun ResultRowDetailed(label: String, value: String, isBold: Boolean = false, isGray: Boolean = false, isDark: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (isGray) Color.Gray else if (isDark) Color.White else Color.Black,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = if (isGray) Color.Gray else if (isDark) Color.White else Color.Black,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
    }
}

data class MonthData(
    val name: String,
    val norm: Double,
    val fact: Double,
    val defaultNightShifts: Double,
    val defaultS4Shifts: Double,
    val defaultAdvanceShifts: Double
)

fun parseNonNegative(input: String): Double = input.toDoubleOrNull()?.takeIf { it >= 0 } ?: 0.0
fun displayInt(input: String): String = input.toDoubleOrNull()?.toInt()?.toString() ?: input