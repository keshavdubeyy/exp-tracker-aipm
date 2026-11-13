package com.yourteam.expensetracker.ui.screens.insights

import android.net.Uri
import com.yourteam.expensetracker.insights.InsightPeriod
import com.yourteam.expensetracker.insights.PeriodKind
import java.time.LocalDate

/** Routes and argument packing for the Insights drill-downs. A period travels as "KIND_startDay_endDay". */
object InsightNav {
    const val ARG_TARGET = "target"
    const val ARG_PERIOD = "period"
    const val CATEGORY = "insights/category"
    const val INSIGHT = "insights/insight"

    fun categoryRoute(category: String, period: InsightPeriod) =
        "$CATEGORY/${Uri.encode(category)}/${encodePeriod(period)}"

    fun insightRoute(id: String, period: InsightPeriod) =
        "$INSIGHT/${Uri.encode(id)}/${encodePeriod(period)}"

    fun encodePeriod(p: InsightPeriod): String = "${p.kind.name}_${p.start.toEpochDay()}_${p.end.toEpochDay()}"

    fun decodePeriod(s: String): InsightPeriod {
        val parts = s.split("_")
        return InsightPeriod(
            kind = runCatching { PeriodKind.valueOf(parts[0]) }.getOrDefault(PeriodKind.MONTH),
            start = LocalDate.ofEpochDay(parts[1].toLong()),
            end = LocalDate.ofEpochDay(parts[2].toLong()),
        )
    }
}
