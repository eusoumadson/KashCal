package org.onekash.kashcal.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Receives the Month + Agenda widget's lifecycle broadcasts and renders through [MonthAgendaWidget].
 * FORK:experimental-widgets
 */
class MonthAgendaWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = MonthAgendaWidget()
}
