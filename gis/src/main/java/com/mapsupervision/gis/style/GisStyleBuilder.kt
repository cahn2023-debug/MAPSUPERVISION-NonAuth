package com.mapsupervision.gis.style

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GisStyleBuilder @Inject constructor() {
    fun buildStyleJson(tileUrlTemplate: String): String =
        """
        {
          "version": 8,
          "name": "field-raster-style",
          "glyphs": "https://orangemug.github.io/font-glyphs/glyphs/{fontstack}/{range}.pbf",
          "sources": {
            "osm_raster": {
              "type": "raster",
              "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
              "tileSize": 256,
              "minzoom": 0,
              "maxzoom": 19
            },
            "vietnam_territory_source": {
              "type": "geojson",
              "data": {
                "type": "FeatureCollection",
                "features": [
                  {
                    "type": "Feature",
                    "properties": {
                      "name": "Qu\u1ea7n \u0111\u1ea3o Ho\u00e0ng Sa",
                      "kind": "archipelago"
                    },
                    "geometry": {
                      "type": "Point",
                      "coordinates": [111.7, 16.5]
                    }
                  },
                  {
                    "type": "Feature",
                    "properties": {
                      "name": "Qu\u1ea7n \u0111\u1ea3o Tr\u01b0\u1eddng Sa",
                      "kind": "archipelago"
                    },
                    "geometry": {
                      "type": "Point",
                      "coordinates": [114.3, 10.5]
                    }
                  }
                ]
              }
            },
            "nodes_source": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } },
            "routes_source": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } },
            "measure_source": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } }
          },
          "layers": [
            { "id": "osm_base", "type": "raster", "source": "osm_raster" },
            {
              "id": "vietnam_archipelago_markers",
              "type": "circle",
              "source": "vietnam_territory_source",
              "paint": {
                "circle-color": "#dc2626",
                "circle-radius": 5,
                "circle-stroke-color": "#ffffff",
                "circle-stroke-width": 2
              }
            },
            {
              "id": "vietnam_archipelago_labels",
              "type": "symbol",
              "source": "vietnam_territory_source",
              "layout": {
                "text-field": ["get", "name"],
                "text-font": ["Noto Sans Regular", "Arial Unicode MS Regular"],
                "text-size": 14,
                "text-offset": [0, 1.2],
                "text-anchor": "top",
                "text-allow-overlap": true,
                "text-ignore-placement": true
              },
              "paint": {
                "text-color": "#991b1b",
                "text-halo-color": "#ffffff",
                "text-halo-width": 1.5
              }
            },
            { "id": "routes", "type": "line", "source": "routes_source", "paint": { "line-color": "#1a73e8", "line-width": 4 } },
            {
              "id": "measure_line",
              "type": "line",
              "source": "measure_source",
              "paint": {
                "line-color": "#ef4444",
                "line-width": 3,
                "line-dasharray": [2, 2]
              }
            },
            {
              "id": "nodes",
              "type": "circle",
              "source": "nodes_source",
              "paint": {
                "circle-color": ["coalesce", ["get", "color"], "#f97316"],
                "circle-radius": 10,
                "circle-stroke-color": "#ffffff",
                "circle-stroke-width": 2.5
              }
            },
            {
              "id": "nodes_labels",
              "type": "symbol",
              "source": "nodes_source",
              "layout": {
                "text-field": ["get", "label"],
                "text-font": ["Noto Sans Regular", "Arial Unicode MS Regular"],
                "text-size": 11,
                "text-offset": [0, 0],
                "text-anchor": "center",
                "text-allow-overlap": true,
                "text-ignore-placement": true
              },
              "paint": {
                "text-color": "#ffffff",
                "text-halo-color": "#ffffff",
                "text-halo-width": 0.0
              }
            }
          ]
        }
        """.trimIndent()
}
