# Maps — version 0.3.0

## User flow

Choose on map opens an online, interactive map. Pan/zoom and tap to choose a point, or enter decimal WGS84 latitude/longitude manually. Show pin recentres the map. Use this location confirms the picker into the editor draft; the activity Save button writes it to Room. Cancelling the picker leaves the draft unchanged. Existing pins can be viewed, replaced, or removed. The venue/name field is separate text, not a reverse-geocoded address.

The initial viewport is Kuala Lumpur (3.139, 101.6869), not the device's current position. Viewer dialogs do not let taps modify the saved point. A point can be saved without a venue name and vice versa. This is one location per activity, not a recorded route.

## SOLID boundaries

- `GeoPoint` validates finite WGS84 coordinates in the domain. An absent pin is null; latitude/longitude zero are valid.
- `JournalController` coordinates a LocationSelected event without importing map SDK classes.
- The Room adapter maps the optional point into two nullable columns.
- `MapLibreLocationAdapter` owns SDK rendering, GeoJSON pin styling, camera updates, and native resource lifecycle.
- `LocationMap` supplies the Compose/lifecycle bridge and a provider-neutral coordinate API.
- `MapConfiguration` supplies the style URL and initial camera configuration. Journal rules do not know which provider renders the map.

The native adapter ignores stale style callbacks and does not retain closed map instances. Compose forwards start/resume/pause/stop/destroy and memory-pressure signals. These lifecycle paths still require device testing.

## Provider and attribution

The configured style is `https://tiles.openfreemap.org/styles/liberty`, documented by [OpenFreeMap's quick start](https://openfreemap.org/quick_start/). It supports MapLibre Native without adding an API key to this project. SDK API baseline: `org.maplibre.gl:android-sdk:11.8.0`, as shown in the [MapLibre native Android quick start](https://maplibre.org/maplibre-native/android/examples/getting-started/). This is a pinned integration baseline, not a latest-version or production-compatibility claim.

The map keeps SDK attribution and also displays visible links for OpenFreeMap, OpenMapTiles, and OpenStreetMap contributors/data licensing. Do not remove those credits. Review the provider's current terms if changing providers, publishing at scale, or introducing offline downloads. This implementation does not request offline regions or bulk tile downloads.

## Connectivity and data

The library fetches style, tile, glyph, and sprite resources from the configured provider and its style-defined hosts. A provider can observe ordinary map requests/IP addresses and the requested viewport. Journal names, notes, effort, and the full activity history are not included in map requests. The selected pin is drawn locally as GeoJSON. Coordinates remain in the local journal database and Android saved-state draft.

Only INTERNET permission is added. No device-location permission or background service is requested. When map resources cannot load, the picker still supports coordinate entry and saving a pin. The offline journal continues working; this is not an offline-map feature or a navigation/safety tool. Web Mercator display clamps camera latitude near the poles while retaining the original WGS84 value.

## Remaining verification

Native rendering, AndroidView/lifecycle integration, network failure/reload behavior, fonts/attribution, ABI/GPU compatibility, and Room-generated validation have not run in this environment. Run the full build and the maps manual checklist on a physical Android device. Coordinates and presentation flows are covered by the executable JVM tests; migration SQL is covered by desktop SQLite tests.
