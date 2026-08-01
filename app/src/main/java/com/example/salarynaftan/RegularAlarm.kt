package com.example.salarynaftan

data class RegularAlarm(
    val id: Long,
    val time: String,
    val isEnabled: Boolean,
    val label: String
) {
    companion object {
        /**
         * Генерирует id, гарантированно не совпадающий ни с одним из existing.
         * Защищает от дублей, если два будильника создаются в одну и ту же миллисекунду.
         */
        fun newId(existing: List<RegularAlarm>): Long {
            val used = existing.mapTo(HashSet()) { it.id }
            var candidate = System.currentTimeMillis()
            while (candidate in used) {
                candidate += 1
            }
            return candidate
        }
    }
}