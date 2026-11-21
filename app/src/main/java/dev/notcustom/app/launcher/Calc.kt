package dev.notcustom.app.launcher

/**
 * Evaluates simple arithmetic typed into search: + - * / ( ) and decimals, "x" or "×" for times.
 * Returns null for anything else, including plain numbers, so typing "7" doesn't show a result.
 */
fun calculate(input: String): Double? {
    val text = input.replace(" ", "").replace('×', '*').replace('x', '*').replace('÷', '/').replace(',', '.')
    if (text.isEmpty() || text.none { it in "+-*/" } || text.any { it !in "0123456789.+-*/()" }) return null
    var pos = 0
    fun peek() = text.getOrNull(pos)
    lateinit var expr: () -> Double
    fun factor(): Double = when (peek()) {
        '-' -> { pos++; -factor() }
        '(' -> { pos++; expr().also { if (peek() != ')') error("missing )"); pos++ } }
        else -> {
            val start = pos
            while (peek()?.let { it.isDigit() || it == '.' } == true) pos++
            text.substring(start, pos).toDouble()
        }
    }
    fun term(): Double {
        var value = factor()
        while (peek() == '*' || peek() == '/') {
            value = if (text[pos++] == '*') value * factor() else value / factor()
        }
        return value
    }
    expr = {
        var value = term()
        while (peek() == '+' || peek() == '-') {
            value = if (text[pos++] == '+') value + term() else value - term()
        }
        value
    }
    return runCatching { expr().takeIf { pos == text.length && it.isFinite() } }.getOrNull()
}

/** "12.0" as "12", otherwise up to 8 decimals without trailing zeros. */
fun formatResult(value: Double): String =
    if (value == Math.rint(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString()
    else "%.8f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.')
