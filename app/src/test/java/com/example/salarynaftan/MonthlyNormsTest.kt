package com.example.salarynaftan

import org.junit.Assert.assertEquals
import org.junit.Test

class MonthlyNormsTest {

    @Test
    fun `list has 12 months`() {
        assertEquals(12, MonthlyNorms.list.size)
    }

    @Test
    fun `MONTH_NAMES_NOMINATIVE has 12 entries`() {
        assertEquals(12, MonthlyNorms.MONTH_NAMES_NOMINATIVE.size)
    }

    @Test
    fun `first month is January`() {
        assertEquals("Январь", MonthlyNorms.list[0].name)
    }

    @Test
    fun `last month is December`() {
        assertEquals("Декабрь", MonthlyNorms.list[11].name)
    }

    @Test
    fun `all norms are positive`() {
        MonthlyNorms.list.forEach { month ->
            org.junit.Assert.assertTrue("Norm for ${month.name} should be > 0", month.norm > 0)
        }
    }

    @Test
    fun `month names match nominative list`() {
        MonthlyNorms.list.forEachIndexed { index, month ->
            assertEquals(MonthlyNorms.MONTH_NAMES_NOMINATIVE[index], month.name)
        }
    }

    @Test
    fun `year norm for 2027 January is 132`() {
        assertEquals(132.0, MonthlyNorms.norm(2027, 0), 0.001)
    }

    @Test
    fun `year norm for 2027 December is 159`() {
        assertEquals(159.0, MonthlyNorms.norm(2027, 11), 0.001)
    }

    @Test
    fun `year norm for 2027 April matches spreadsheet`() {
        assertEquals(153.0, MonthlyNorms.norm(2027, 3), 0.001)
    }

    @Test
    fun `year norm for 2027 August matches spreadsheet`() {
        assertEquals(154.0, MonthlyNorms.norm(2027, 7), 0.001)
    }

    @Test
    fun `year norm for 2035 September matches spreadsheet`() {
        assertEquals(140.0, MonthlyNorms.norm(2035, 8), 0.001)
    }

    @Test
    fun `supported years covers 2026 to 2040`() {
        assertEquals(2026, MonthlyNorms.supportedYears().first)
        assertEquals(2040, MonthlyNorms.supportedYears().last)
    }

    @Test
    fun `unsupported year falls back to default norm`() {
        assertEquals(MonthlyNorms.list[0].norm, MonthlyNorms.norm(2025, 0), 0.001)
    }

    // ===== Нормы Графика №2 (40-часовая рабочая неделя) из производственного календаря =====

    @Test
    fun `2026 graph2 January norm is 151 from PDF`() {
        assertEquals(151.0, MonthlyNorms.norm(2026, 0, ScheduleType.GRAPH_2), 0.001)
    }

    @Test
    fun `2026 graph2 March norm is 176 from PDF`() {
        assertEquals(176.0, MonthlyNorms.norm(2026, 2, ScheduleType.GRAPH_2), 0.001)
    }

    @Test
    fun `2026 graph2 May norm is 159 from PDF`() {
        assertEquals(159.0, MonthlyNorms.norm(2026, 4, ScheduleType.GRAPH_2), 0.001)
    }

    @Test
    fun `2026 graph2 December norm is 174 from PDF`() {
        assertEquals(174.0, MonthlyNorms.norm(2026, 11, ScheduleType.GRAPH_2), 0.001)
    }

    @Test
    fun `2027 graph2 January norm is 151`() {
        assertEquals(151.0, MonthlyNorms.norm(2027, 0, ScheduleType.GRAPH_2), 0.001)
    }

    @Test
    fun `2027 graph2 March norm is 176`() {
        assertEquals(176.0, MonthlyNorms.norm(2027, 2, ScheduleType.GRAPH_2), 0.001)
    }

    @Test
    fun `2027 graph2 December norm is 182`() {
        assertEquals(182.0, MonthlyNorms.norm(2027, 11, ScheduleType.GRAPH_2), 0.001)
    }

    @Test
    fun `2035 graph2 September norm is 160`() {
        assertEquals(160.0, MonthlyNorms.norm(2035, 8, ScheduleType.GRAPH_2), 0.001)
    }

    @Test
    fun `2040 graph2 October norm is 184`() {
        assertEquals(184.0, MonthlyNorms.norm(2040, 9, ScheduleType.GRAPH_2), 0.001)
    }

    @Test
    fun `2040 graph2 December norm is 158`() {
        assertEquals(158.0, MonthlyNorms.norm(2040, 11, ScheduleType.GRAPH_2), 0.001)
    }

    @Test
    fun `graph1 2026 January norm is 132 from PDF`() {
        assertEquals(132.0, MonthlyNorms.norm(2026, 0, ScheduleType.GRAPH_1), 0.001)
    }

    @Test
    fun `graph1 2026 December norm is 152 from PDF`() {
        assertEquals(152.0, MonthlyNorms.norm(2026, 11, ScheduleType.GRAPH_1), 0.001)
    }

    @Test
    fun `graph1 2029 January norm is 147 from spreadsheet`() {
        assertEquals(147.0, MonthlyNorms.norm(2029, 0, ScheduleType.GRAPH_1), 0.001)
    }

    @Test
    fun `graph1 2030 January norm is 140 from spreadsheet`() {
        assertEquals(140.0, MonthlyNorms.norm(2030, 0, ScheduleType.GRAPH_1), 0.001)
    }

    @Test
    fun `graph1 2031 January norm is 139 from spreadsheet`() {
        assertEquals(139.0, MonthlyNorms.norm(2031, 0, ScheduleType.GRAPH_1), 0.001)
    }

    @Test
    fun `graph1 2034 January norm is 146 from spreadsheet`() {
        assertEquals(146.0, MonthlyNorms.norm(2034, 0, ScheduleType.GRAPH_1), 0.001)
    }

    @Test
    fun `graph1 2035 January norm is 147 from spreadsheet`() {
        assertEquals(147.0, MonthlyNorms.norm(2035, 0, ScheduleType.GRAPH_1), 0.001)
    }

    @Test
    fun `graph1 2040 January norm is 146 from spreadsheet`() {
        assertEquals(146.0, MonthlyNorms.norm(2040, 0, ScheduleType.GRAPH_1), 0.001)
    }

    // ===== Проверка соответствия всей таблице из Google (2026–2040) =====

    @Test
    fun `all graph1 norms 2026-2040 match spreadsheet`() {
        // Данные из публичной Google-таблицы (колонка "Норма при 35-час. неделе")
        val expected = mapOf(
            2026 to listOf(132.0, 140.0, 154.0, 145.0, 139.0, 154.0, 153.0, 147.0, 154.0, 154.0, 146.0, 152.0),
            2027 to listOf(132.0, 140.0, 154.0, 153.0, 139.0, 154.0, 153.0, 154.0, 154.0, 147.0, 154.0, 159.0),
            2028 to listOf(139.0, 147.0, 153.0, 132.0, 146.0, 154.0, 140.0, 161.0, 147.0, 154.0, 146.0, 140.0),
            2029 to listOf(147.0, 140.0, 146.0, 138.0, 146.0, 147.0, 146.0, 161.0, 140.0, 161.0, 146.0, 138.0),
            2030 to listOf(140.0, 140.0, 139.0, 153.0, 138.0, 140.0, 153.0, 154.0, 147.0, 161.0, 139.0, 145.0),
            2031 to listOf(139.0, 140.0, 146.0, 145.0, 139.0, 147.0, 153.0, 147.0, 154.0, 161.0, 132.0, 152.0),
            2032 to listOf(132.0, 140.0, 154.0, 153.0, 139.0, 154.0, 153.0, 154.0, 154.0, 147.0, 154.0, 159.0),
            2033 to listOf(139.0, 140.0, 153.0, 147.0, 139.0, 154.0, 147.0, 161.0, 154.0, 147.0, 147.0, 154.0),
            2034 to listOf(146.0, 140.0, 153.0, 132.0, 146.0, 154.0, 140.0, 161.0, 147.0, 154.0, 146.0, 140.0),
            2035 to listOf(147.0, 140.0, 146.0, 146.0, 139.0, 147.0, 146.0, 161.0, 140.0, 161.0, 146.0, 138.0),
            2036 to listOf(140.0, 147.0, 146.0, 145.0, 139.0, 147.0, 153.0, 147.0, 154.0, 161.0, 132.0, 152.0),
            2037 to listOf(132.0, 140.0, 154.0, 145.0, 139.0, 154.0, 153.0, 147.0, 154.0, 154.0, 146.0, 152.0),
            2038 to listOf(132.0, 140.0, 154.0, 153.0, 139.0, 154.0, 153.0, 154.0, 154.0, 147.0, 154.0, 159.0),
            2039 to listOf(139.0, 140.0, 153.0, 139.0, 147.0, 154.0, 147.0, 161.0, 154.0, 147.0, 147.0, 154.0),
            2040 to listOf(146.0, 147.0, 146.0, 146.0, 138.0, 147.0, 146.0, 161.0, 140.0, 161.0, 146.0, 138.0)
        )
        expected.forEach { (year, norms) ->
            norms.forEachIndexed { month, norm ->
                assertEquals(
                    "Норма $year/${month + 1}",
                    norm,
                    MonthlyNorms.norm(year, month, ScheduleType.GRAPH_1),
                    0.001
                )
            }
        }
    }

    @Test
    fun `all graph2 norms 2026-2040 match spreadsheet`() {
        // Данные из публичной Google-таблицы (колонка "Норма при 40-час. неделе")
        val expected = mapOf(
            2026 to listOf(151.0, 160.0, 176.0, 166.0, 159.0, 176.0, 175.0, 168.0, 176.0, 176.0, 167.0, 174.0),
            2027 to listOf(151.0, 160.0, 176.0, 175.0, 159.0, 176.0, 175.0, 176.0, 176.0, 168.0, 176.0, 182.0),
            2028 to listOf(159.0, 168.0, 175.0, 151.0, 167.0, 176.0, 160.0, 184.0, 168.0, 176.0, 167.0, 160.0),
            2029 to listOf(168.0, 160.0, 167.0, 158.0, 167.0, 168.0, 167.0, 184.0, 160.0, 184.0, 167.0, 158.0),
            2030 to listOf(160.0, 160.0, 159.0, 175.0, 158.0, 160.0, 175.0, 176.0, 168.0, 184.0, 159.0, 166.0),
            2031 to listOf(159.0, 160.0, 167.0, 166.0, 159.0, 168.0, 175.0, 168.0, 176.0, 184.0, 151.0, 174.0),
            2032 to listOf(151.0, 160.0, 176.0, 175.0, 159.0, 176.0, 175.0, 176.0, 176.0, 168.0, 176.0, 182.0),
            2033 to listOf(159.0, 160.0, 175.0, 168.0, 159.0, 176.0, 168.0, 184.0, 176.0, 168.0, 168.0, 176.0),
            2034 to listOf(167.0, 160.0, 175.0, 151.0, 167.0, 176.0, 160.0, 184.0, 168.0, 176.0, 167.0, 160.0),
            2035 to listOf(168.0, 160.0, 167.0, 167.0, 159.0, 168.0, 167.0, 184.0, 160.0, 184.0, 167.0, 158.0),
            2036 to listOf(160.0, 168.0, 167.0, 166.0, 159.0, 168.0, 175.0, 168.0, 176.0, 184.0, 151.0, 174.0),
            2037 to listOf(151.0, 160.0, 176.0, 166.0, 159.0, 176.0, 175.0, 168.0, 176.0, 176.0, 167.0, 174.0),
            2038 to listOf(151.0, 160.0, 176.0, 175.0, 159.0, 176.0, 175.0, 176.0, 176.0, 168.0, 176.0, 182.0),
            2039 to listOf(159.0, 160.0, 175.0, 159.0, 168.0, 176.0, 168.0, 184.0, 176.0, 168.0, 168.0, 176.0),
            2040 to listOf(167.0, 168.0, 167.0, 167.0, 158.0, 168.0, 167.0, 184.0, 160.0, 184.0, 167.0, 158.0)
        )
        expected.forEach { (year, norms) ->
            norms.forEachIndexed { month, norm ->
                assertEquals(
                    "Норма $year/${month + 1}",
                    norm,
                    MonthlyNorms.norm(year, month, ScheduleType.GRAPH_2),
                    0.001
                )
            }
        }
    }
}
