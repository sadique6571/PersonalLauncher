# Weather imagery

These are bundled illustrations of conditions, not live camera or satellite feeds of the configured location. Forecast values and day/night state still come from the weather provider. The fixed lunar image is not a calculation of today's lunar phase.

- `weather_moon_nasa.jpg`: NASA Scientific Visualization Studio, LRO/LOLA/LROC visualization, https://svs.gsfc.nasa.gov/5048/
- `weather_sun_sdo.jpg`: NASA/SDO HMI color-intensity solar disc, captured 2026-11-10 from https://sdo.gsfc.nasa.gov/assets/img/latest/latest_512_HMIIC.jpg . This is colorized scientific imagery. The disc mask excludes the source image's footer.
- `weather_clouds_noaa.jpg`: NOAA/NWS JetStream, https://www.noaa.gov/sites/default/files/2022-05/Cumulus.jpg
- `weather_overcast_noaa.jpg`: NOAA/NWS JetStream, https://www.noaa.gov/sites/default/files/2022-05/Nimbostratus.jpg
- `weather_storm_noaa.jpg`: NOAA/NWS JetStream, https://www.noaa.gov/sites/default/files/2022-05/Cumulonimbus.jpg
- `weather_fog_noaa.jpg`: NOAA/NWS JetStream, https://www.noaa.gov/sites/default/files/2022-05/fog.jpg

JetStream's educational materials permit reuse: https://www.noaa.gov/jetstream . Credits are also shown in the weather forecast panel. No agency endorsement is implied. Third-party credited rain/snow photos were not included.

All decoding runs on the existing weather worker. Source backgrounds are bounded to 512 pixels, the solar disc to 128 pixels, and the photographic cloud symbol to 160×107. Rain and snow are small Canvas overlays, capped at 42 drops / 24 flakes, 8–15 fps only while visible and enabled. Static conditions have no animation loop.
