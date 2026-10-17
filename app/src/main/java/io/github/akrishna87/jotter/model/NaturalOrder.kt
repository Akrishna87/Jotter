package io.github.akrishna87.jotter.model

/**
 * Orders text the way people count: "App2" before "App10", "Chapter 9" before "Chapter 12".
 * Runs of digits compare by their value; everything else compares ignoring case.
 */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val ei = digitsEnd(a, i)
                val ej = digitsEnd(b, j)
                val na = a.substring(i, ei).trimStart('0')
                val nb = b.substring(j, ej).trimStart('0')
                // A longer number (without leading zeros) is bigger; same length compares digit by digit.
                val byValue = if (na.length != nb.length) na.length - nb.length else na.compareTo(nb)
                if (byValue != 0) return byValue
                i = ei
                j = ej
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        val byLength = (a.length - i) - (b.length - j)
        // Equal apart from case or leading zeros: keep a stable order anyway.
        return if (byLength != 0) byLength else a.compareTo(b)
    }

    private fun digitsEnd(s: String, from: Int): Int {
        var k = from
        while (k < s.length && s[k].isDigit()) k++
        return k
    }
}
