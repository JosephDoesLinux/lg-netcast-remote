package dev.netcastremote.wear

import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class WatchWearService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            val channel = map.getString("channel") ?: return@forEach
            WatchState.channel = channel
            getSharedPreferences("netcast", 0).edit().putString("channel", channel).apply()
            TileService.getUpdater(this).requestUpdate(NetcastTileService::class.java)
        }
    }
}

class NetcastTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val channel = getSharedPreferences("netcast", 0).getString("channel", "TV") ?: "TV"
        val layout = LayoutElementBuilders.Layout.Builder()
            .setRoot(LayoutElementBuilders.Text.Builder().setText(channel).build())
            .build()
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(TimelineBuilders.TimelineEntry.Builder().setLayout(layout).build())
                    .build(),
            )
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion("1").build())
}

class ChannelComplication : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType) = text("NetCast")

    override suspend fun onComplicationRequest(request: ComplicationRequest) =
        text(getSharedPreferences("netcast", 0).getString("channel", "TV") ?: "TV")

    private fun text(value: String) = ShortTextComplicationData.Builder(
        PlainComplicationText.Builder(value).build(),
        PlainComplicationText.Builder(value).build(),
    ).build()
}
