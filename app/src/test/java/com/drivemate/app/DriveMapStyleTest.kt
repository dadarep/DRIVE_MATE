package com.drivemate.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DriveMapStyleTest {
    @Test fun dayAndNightStylesHaveValidSourcesAndHiddenNavigationOverlays() {
        for(night in listOf(false,true)){
            val style=JSONObject(DriveMapStyle.json(night))
            val sources=style.getJSONObject("sources")
            val layers=style.getJSONArray("layers")
            val byId=(0 until layers.length()).associate { val l=layers.getJSONObject(it);l.getString("id") to l }
            assertTrue(DriveMapStyle.roads.all{byId.containsKey(it)})
            for(layer in byId.values){if(layer.has("source"))assertTrue(sources.has(layer.getString("source")))}
            for(id in DriveMapStyle.overlays)assertEquals("none",byId.getValue(id).getJSONObject("layout").getString("visibility"))
        }
    }
    @Test fun labelsPreferKoreanAndNightMapUsesSeparatePalette() {
        val day=JSONObject(DriveMapStyle.json(false)).getJSONArray("layers")
        val night=JSONObject(DriveMapStyle.json(true)).getJSONArray("layers")
        assertNotEquals(day.getJSONObject(0).getJSONObject("paint").getString("background-color"),night.getJSONObject(0).getJSONObject("paint").getString("background-color"))
        val labels=(0 until day.length()).map{day.getJSONObject(it)}.single{it.getString("id")=="road-names"}
        assertTrue(labels.getJSONObject("layout").getJSONArray("text-field").toString().contains("name:ko"))
    }
}
