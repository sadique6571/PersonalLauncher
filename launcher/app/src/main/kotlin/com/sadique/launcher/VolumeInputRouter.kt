package com.sadique.launcher

/** Tracks one parser per input device so unrelated devices cannot release a held key. */
internal class VolumeInputRouter(private val controller: FastVolumeController) {
    private val parsers = HashMap<String, FastVolumeController.Parser>()
    private var active: String? = null
    private var key = 0

    fun line(line: String) {
        val start = line.indexOf(DEVICE_PREFIX)
        if (start < 0) return
        val suffix = start + DEVICE_PREFIX.length
        var end = suffix
        while (end < line.length && Character.isDigit(line[end])) end++
        if (end == suffix) return
        val device = line.substring(start, end)

        if (line.startsWith("remove device") || line.startsWith("add device")) {
            parsers.remove(device)
            if (device == active) {
                controller.stop()
                clear()
            }
            return
        }
        val parser = parsers.getOrPut(device) {
            FastVolumeController.Parser(object : FastVolumeController.Parser.Sink {
                override fun down(value: Int) {
                    active = device
                    key = value
                    controller.down(value)
                }

                override fun up(value: Int) {
                    if (device == active && key == value) {
                        controller.up(value)
                        clear()
                    }
                }

                override fun unknownUp() {
                    if (device == active) {
                        controller.unknownUp()
                        clear()
                    }
                }

                override fun stop() {
                    if (device == active) {
                        controller.stop()
                        clear()
                    }
                }
            })
        }
        parser.line(line)
    }

    private fun clear() {
        active = null
        key = 0
    }

    private companion object {
        const val DEVICE_PREFIX = "/dev/input/event"
    }
}
