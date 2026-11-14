package com.sadique.launcher

/** Vendor source numbers for passthrough inputs, such as Xiaomi's EXTSRC_PLAY route. */
object InputSources {

    @JvmStatic
    fun mapped(mappings: List<String?>?, inputId: String?): Int? {
        if (inputId == null || mappings == null) return null
        val input = inputId.substring(inputId.lastIndexOf('/') + 1)
        for (mapping in mappings) {
            if (mapping == null) continue
            val equals = mapping.indexOf('=')
            if (equals <= 0 || mapping.substring(0, equals).trim() != input) continue
            val value = mapping.substring(equals + 1).trim()
            if (value == "-") return -1
            try {
                return value.toInt()
            } catch (_: NumberFormatException) {
                return null
            }
        }
        return null
    }
}
