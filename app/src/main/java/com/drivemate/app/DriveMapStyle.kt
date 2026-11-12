package com.drivemate.app

import org.json.JSONArray
import org.json.JSONObject

/** DRIVE MATE styling over the public OpenMapTiles schema; no third-party style is copied. */
internal object DriveMapStyle {
    const val tiles = "https://tiles.openfreemap.org/planet/latest"
    const val glyphs = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf"
    val roads = arrayOf("street", "arterial", "motorway")
    val overlays = arrayOf("route-tail", "route-outline", "route-line", "route-directions", "destination-dot", "destination-label", "selection-dot", "position-halo", "position-dot", "position-arrow")
    fun json(night: Boolean): String {
        val sources = JSONObject().put("basemap", JSONObject().put("type", "vector").put("url", tiles))
        for (id in listOf("route-data", "route-tail-data", "destination-data", "position-data", "selection-data")) {
            sources.put(id, JSONObject().put("type", "geojson").put("data", JSONObject().put("type", "FeatureCollection").put("features", JSONArray())))
        }
        val layers = JSONArray()
        fun add(id: String, type: String, paint: String, sourceLayer: String? = null, filter: String? = null,
                layout: String = "{}", minZoom: Int? = null, source: String? = null) {
            val layer = JSONObject().put("id", id).put("type", type).put("paint", JSONObject(paint)).put("layout", JSONObject(layout))
            if (sourceLayer != null) layer.put("source", "basemap").put("source-layer", sourceLayer)
            if (source != null) layer.put("source", source).getJSONObject("layout").put("visibility", "none")
            if (filter != null) layer.put("filter", JSONArray(filter))
            if (minZoom != null) layer.put("minzoom", minZoom)
            layers.put(layer)
        }
        val background = if (night) "#17232E" else "#F0F1ED"
        val water = if (night) "#203C50" else "#B9DCE9"
        val park = if (night) "#253D38" else "#D8E7D3"
        val building = if (night) "#2C3A46" else "#DCE1E0"
        val border = if (night) "#1B2630" else "#D2D6D4"
        val street = if (night) "#3E4B57" else "#FFFFFF"
        val arterial = if (night) "#626052" else "#FFF0BE"
        val motorway = if (night) "#7C7050" else "#F6DA98"
        val label = if (night) "#BBCBD7" else "#536676"
        val round = """{"line-cap":"round","line-join":"round"}"""
        val name = """["coalesce",["get","name:ko"],["get","name"],["get","name:en"],""]"""
        add("background", "background", """{"background-color":"$background"}""")
        add("green-land", "fill", """{"fill-color":"$park","fill-opacity":0.8}""", "landcover", """["in","class","wood","grass"]""")
        add("parks", "fill", """{"fill-color":"$park"}""", "park")
        add("water", "fill", """{"fill-color":"$water"}""", "water")
        add("streams", "line", """{"line-color":"$water","line-width":["interpolate",["linear"],["zoom"],12,1,18,6]}""", "waterway", layout=round)
        add("buildings", "fill", """{"fill-color":"$building","fill-outline-color":"$border"}""", "building", minZoom=14)
        val kinds = listOf(
            Triple("street", """["in","class","minor","service","tertiary"]""", street),
            Triple("arterial", """["in","class","primary","secondary"]""", arterial),
            Triple("motorway", """["in","class","motorway","trunk"]""", motorway))
        for ((index, road) in kinds.withIndex()) {
            val width = 4.5 + index * 3
            add("${road.first}-border", "line", """{"line-color":"$border","line-width":["interpolate",["exponential",1.25],["zoom"],9,${index*0.3+0.7},16,${width+2},19,${width*4+2}]}""", "transportation", road.second, round)
            add(road.first, "line", """{"line-color":"${road.third}","line-width":["interpolate",["exponential",1.25],["zoom"],9,${index*0.3+0.3},16,$width,19,${width*4}]}""", "transportation", road.second, round)
        }
        add("road-names", "symbol", """{"text-color":"$label","text-halo-color":"$background","text-halo-width":1.8}""", "transportation_name",
            """["in","class","motorway","trunk","primary","secondary","tertiary"]""",
            """{"symbol-placement":"line","symbol-spacing":380,"text-field":$name,"text-font":["Noto Sans Regular"],"text-size":["interpolate",["linear"],["zoom"],12,13,18,18],"text-padding":12}""", 12)
        add("district-names", "symbol", """{"text-color":"$label","text-halo-color":"$background","text-halo-width":2}""", "place",
            """["in","class","city","town","suburb","quarter","neighbourhood"]""",
            """{"text-field":$name,"text-font":["Noto Sans Regular"],"text-size":["interpolate",["linear"],["zoom"],8,13,16,20],"text-padding":24}""", 8)
        add("route-tail", "line", """{"line-color":"${if(night) "#72A7FF" else "#2868EC"}","line-width":5,"line-dasharray":[1.2,1.2]}""", layout="""{"line-cap":"butt","line-join":"round"}""", source="route-tail-data")
        add("route-outline", "line", """{"line-color":"${if(night) "#13202C" else "#FFFFFF"}","line-width":13}""", layout=round, source="route-data")
        add("route-line", "line", """{"line-color":"${if(night) "#72A7FF" else "#2868EC"}","line-width":8}""", layout=round, source="route-data")
        add("route-directions", "symbol", "{}", layout="""{"symbol-placement":"line","symbol-spacing":160,"icon-image":"route-direction","icon-size":0.65,"icon-rotation-alignment":"map","icon-pitch-alignment":"map","icon-allow-overlap":false,"icon-keep-upright":false}""", minZoom=14, source="route-data")
        add("destination-dot", "circle", """{"circle-radius":10,"circle-color":"#EE5960","circle-stroke-width":4,"circle-stroke-color":"#FFFFFF"}""", source="destination-data")
        add("destination-label", "symbol", """{"text-color":"${if(night) "#F5F8FF" else "#233B50"}","text-halo-color":"$background","text-halo-width":3}""",
            layout="""{"text-field":["get","name"],"text-font":["Noto Sans Regular"],"text-size":19,"text-offset":[0,-1.9],"text-anchor":"bottom","text-max-width":12,"text-allow-overlap":true}""", source="destination-data")
        add("selection-dot", "circle", """{"circle-radius":11,"circle-color":"#2868EC","circle-stroke-width":4,"circle-stroke-color":"#FFFFFF"}""", source="selection-data")
        add("position-halo", "circle", """{"circle-radius":27,"circle-color":"#2868EC","circle-opacity":0.16}""", source="position-data")
        add("position-dot", "circle", """{"circle-radius":10,"circle-color":"#2868EC","circle-stroke-width":4,"circle-stroke-color":"#FFFFFF","circle-opacity":["case",["get","headingKnown"],0,1],"circle-stroke-opacity":["case",["get","headingKnown"],0,1]}""", source="position-data")
        add("position-arrow", "symbol", """{"icon-opacity":["case",["get","headingKnown"],1,0]}""", layout="""{"icon-image":"vehicle-arrow","icon-size":0.8,"icon-rotate":["get","bearing"],"icon-rotation-alignment":"map","icon-pitch-alignment":"viewport","icon-allow-overlap":true,"icon-ignore-placement":true}""", source="position-data")
        return JSONObject().put("version",8).put("name","DRIVE MATE · Road focus").put("glyphs",glyphs).put("sources",sources).put("layers",layers).toString()
    }
}
