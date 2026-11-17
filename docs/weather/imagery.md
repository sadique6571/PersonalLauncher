# Weather imagery

These are bundled illustrations of conditions, not live camera or satellite feeds of the configured location. Forecast values and day/night state come from Open-Meteo. The fixed lunar image is not a calculation of today's lunar phase.

- `weather_moon_nasa.jpg`: NASA Scientific Visualization Studio, LRO/LOLA/LROC visualization, https://svs.gsfc.nasa.gov/5048/
- `weather_sun_sdo.jpg`: NASA/SDO HMI color-intensity solar disc, captured 2026-11-10 from https://sdo.gsfc.nasa.gov/assets/img/latest/latest_512_HMIIC.jpg . This is colorized scientific imagery. The disc mask excludes the source image's footer.
- `weather_clouds_noaa.jpg`: NOAA/NWS JetStream, https://www.noaa.gov/sites/default/files/2022-05/Cumulus.jpg
- `weather_overcast_noaa.jpg`: NOAA/NWS JetStream, https://www.noaa.gov/sites/default/files/2022-05/Nimbostratus.jpg
- `weather_storm_noaa.jpg`: NOAA/NWS JetStream, https://www.noaa.gov/sites/default/files/2022-05/Cumulonimbus.jpg
- `weather_fog_noaa.jpg`: NOAA/NWS JetStream, https://www.noaa.gov/sites/default/files/2022-05/fog.jpg

JetStream's educational materials permit reuse: https://www.noaa.gov/jetstream . Credits are also shown in the weather forecast panel. No agency endorsement is implied. Third-party credited rain/snow photos were not included.

Weather background photos and the solar disc are prepared by the weather worker. Background photos are decoded at a bounded size, the solar disc is rendered at 128 × 128, and the cloud symbol uses a 160 × 107 image. Rain and snow overlays use up to 42 drops and 24 flakes. The background animation runs for active precipitation scenes while visible and enabled.
