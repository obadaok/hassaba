package com.platinum.vip.hasiba

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

object CalculatorEngine {

    fun evaluate(expression: String, roundToFour: Boolean = false): String {
        if (expression.isBlank()) return ""

        var expr = expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace(",", "")
            .trim()

        if (!isValidExpression(expr)) return "خطأ في الصيغة"

        expr = preprocessPercent(expr)

        return try {
            val result = evaluateRPN(expr)
            val formatted = formatResult(result)
            if (roundToFour) {
                roundToFourDecimalPlaces(formatted)
            } else {
                formatted
            }
        } catch (e: ArithmeticException) {
            "خطأ (قسمة على صفر)"
        } catch (e: Exception) {
            e.printStackTrace()
            "خطأ"
        }
    }

    private fun isValidExpression(expr: String): Boolean {
        if (expr.isEmpty()) return false
        val last = expr.last()
        if (last in "+-*/%") return false
        var prevWasOp = true
        for (ch in expr) {
            when {
                ch.isDigit() || ch == '.' -> prevWasOp = false
                ch in "+-*/%" -> {
                    if (prevWasOp && ch != '-') return false
                    prevWasOp = true
                }
                ch == '(' -> prevWasOp = true
                ch == ')' -> prevWasOp = false
                else -> return false
            }
        }
        return true
    }

    private fun preprocessPercent(expr: String): String {
        var result = expr
        val percentPattern = Regex("(\\d+(?:\\.\\d+)?)%")
        result = percentPattern.replace(result) { match ->
            val num = match.groupValues[1]
            "($num/100)"
        }
        return result
    }

    private fun toRPN(expr: String): List<String> {
        val output = mutableListOf<String>()
        val operators = Stack<String>()
        val precedence = mapOf("+" to 1, "-" to 1, "*" to 2, "/" to 2, "%" to 2)
        var i = 0
        val n = expr.length

        while (i < n) {
            val c = expr[i]
            when {
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < n && (expr[i].isDigit() || expr[i] == '.')) i++
                    val number = expr.substring(start, i)
                    output.add(number)
                    continue
                }
                c == '(' -> {
                    operators.push("(")
                }
                c == ')' -> {
                    while (operators.isNotEmpty() && operators.peek() != "(") {
                        output.add(operators.pop())
                    }
                    if (operators.isNotEmpty() && operators.peek() == "(") operators.pop()
                }
                c in "+-*/%" -> {
                    val isUnary = (c == '-') && (i == 0 || expr[i - 1] == '(' || expr[i - 1] in "+-*/%")
                    if (isUnary) {
                        val start = i
                        i++
                        while (i < n && (expr[i].isDigit() || expr[i] == '.')) i++
                        val number = expr.substring(start, i)
                        output.add(number)
                        continue
                    }
                    val op = c.toString()
                    while (operators.isNotEmpty() && operators.peek() != "(" &&
                        precedence[operators.peek()]!! >= precedence[op]!!
                    ) {
                        output.add(operators.pop())
                    }
                    operators.push(op)
                }
            }
            i++
        }

        while (operators.isNotEmpty()) {
            output.add(operators.pop())
        }
        return output
    }

    private fun evaluateRPN(expr: String): BigDecimal {
        val rpn = toRPN(expr)
        val stack = Stack<BigDecimal>()
        for (token in rpn) {
            when (token) {
                "+" -> {
                    val b = stack.pop()
                    val a = stack.pop()
                    stack.push(a.add(b))
                }
                "-" -> {
                    val b = stack.pop()
                    val a = stack.pop()
                    stack.push(a.subtract(b))
                }
                "*" -> {
                    val b = stack.pop()
                    val a = stack.pop()
                    stack.push(a.multiply(b))
                }
                "/" -> {
                    val b = stack.pop()
                    val a = stack.pop()
                    if (b.compareTo(BigDecimal.ZERO) == 0) throw ArithmeticException()
                    stack.push(a.divide(b, 20, RoundingMode.HALF_UP))
                }
                "%" -> {
                    val b = stack.pop()
                    val a = stack.pop()
                    stack.push(a.multiply(b).divide(BigDecimal(100), 20, RoundingMode.HALF_UP))
                }
                else -> {
                    stack.push(BigDecimal(token))
                }
            }
        }
        return stack.pop()
    }

    private fun formatResult(result: BigDecimal): String {
        if (result.compareTo(BigDecimal.ZERO) == 0) return "0"
        val stripped = result.stripTrailingZeros()
        val plain = stripped.toPlainString()
        val parts = plain.split(".")
        val integerPart = parts[0]
        val decimalPart = if (parts.size > 1) parts[1] else ""

        val formattedInteger = integerPart.reversed().chunked(3).joinToString(",").reversed()

        return if (decimalPart.isNotEmpty()) "$formattedInteger.$decimalPart" else formattedInteger
    }

    private fun roundToFourDecimalPlaces(numberStr: String): String {
        val bigDecimal = BigDecimal(numberStr.replace(",", ""))
        val rounded = bigDecimal.setScale(4, RoundingMode.HALF_UP)
        val stripped = rounded.stripTrailingZeros()
        return stripped.toPlainString()
    }

    fun formatWithLimit(numberStr: String, decimalPlaces: Int = 4): String {
        val bigDecimal = BigDecimal(numberStr.replace(",", ""))
        val rounded = bigDecimal.setScale(decimalPlaces, RoundingMode.HALF_UP)
        val stripped = rounded.stripTrailingZeros()
        return stripped.toPlainString()
    }
}
