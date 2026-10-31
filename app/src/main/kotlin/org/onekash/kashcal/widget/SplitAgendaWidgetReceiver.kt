package org.onekash.kashcal.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Receives the Split Agenda widget's lifecycle broadcasts and renders through [SplitAgendaWidget].
 * FORK:experimental-widgets
 */
class SplitAgendaWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = SplitAgendaWidget()
}
