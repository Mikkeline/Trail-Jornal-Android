package dev.michelle.trails

import java.math.BigDecimal

internal fun formatKm(meters: Long): String {
    return BigDecimal.valueOf(meters, 3).stripTrailingZeros().toPlainString()
}
