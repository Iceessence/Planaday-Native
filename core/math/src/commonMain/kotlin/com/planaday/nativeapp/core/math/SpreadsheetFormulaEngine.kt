package com.planaday.nativeapp.core.math

import com.planaday.nativeapp.core.model.WorksheetModel
import kotlin.math.*

sealed interface SheetValue {
    data class NumberVal(val number: Double) : SheetValue
    data class TextVal(val text: String) : SheetValue
    data class BoolVal(val flag: Boolean) : SheetValue
    data class ErrorVal(val code: String) : SheetValue // #REF!, #DIV/0!, #CIRC!, #VALUE!, #NAME?
    data object EmptyVal : SheetValue

    fun displayString(): String = when (this) {
        is NumberVal -> if (number == floor(number) && abs(number) < 1e15) number.toLong().toString() else number.toString()
        is TextVal -> text
        is BoolVal -> if (flag) "TRUE" else "FALSE"
        is ErrorVal -> code
        EmptyVal -> ""
    }
}

data class EvaluatedCell(
    val raw: String,
    val value: SheetValue,
    val error: String? = (value as? SheetValue.ErrorVal)?.code,
)

/**
 * Clean-room sparse dependency-graph spreadsheet formula engine (R-0424..R-0436, CONT-006, CONT-007).
 * - Full recursive-descent formula AST supporting composite expressions like `=SUM(A1:A3)+1` (CONT-007).
 * - Explicit cross-sheet resolution returning `#REF!` on missing sheets (CONT-006).
 * - Exact zero-length `LEFT("abc",0)` / `RIGHT("abc",0)` semantics (CONT-007).
 * - Cycle detection returning `#CIRC!` without stack overflow.
 * - Sparse cell storage handling 10,000+ cells cleanly with memoized DAG evaluation.
 */
object SpreadsheetFormulaEngine {
    private class FormulaError(val code: String) : RuntimeException(code)

    fun evaluateWorkbook(
        sheets: List<WorksheetModel>,
        todayIso: String = "2026-11-25",
    ): Map<String, Map<String, EvaluatedCell>> {
        val byId = sheets.associateBy { it.id }
        val byNameLower = sheets.associateBy { it.name.lowercase() }
        val cache = mutableMapOf<Pair<String, String>, EvaluatedCell>()
        val visiting = mutableSetOf<Pair<String, String>>()

        fun resolveSheet(refName: String?): WorksheetModel {
            if (refName == null) throw FormulaError("#REF!")
            val clean = refName.trim().removeSurrounding("'")
            return byId[clean] ?: byNameLower[clean.lowercase()] ?: throw FormulaError("#REF!")
        }

        fun evalCell(sheet: WorksheetModel, addrInput: String): SheetValue {
            val addr = addrInput.uppercase()
            val key = sheet.id to addr
            cache[key]?.let { return it.value }
            if (key in visiting) {
                val err = SheetValue.ErrorVal("#CIRC!")
                return err
            }
            val cellInput = sheet.cells[addr] ?: return SheetValue.EmptyVal
            val raw = cellInput.raw.trim()
            if (raw.isEmpty()) return SheetValue.EmptyVal

            if (!raw.startsWith("=")) {
                val num = raw.toDoubleOrNull()
                val v = when {
                    num != null -> SheetValue.NumberVal(num)
                    raw.equals("TRUE", ignoreCase = true) -> SheetValue.BoolVal(true)
                    raw.equals("FALSE", ignoreCase = true) -> SheetValue.BoolVal(false)
                    else -> SheetValue.TextVal(cellInput.raw)
                }
                cache[key] = EvaluatedCell(cellInput.raw, v)
                return v
            }

            visiting.add(key)
            val result: SheetValue = try {
                val parser = FormulaParser(
                    expr = raw.substring(1),
                    currentSheet = sheet,
                    todayIso = todayIso,
                    resolveSheet = ::resolveSheet,
                    evalCell = ::evalCell,
                )
                parser.evaluate()
            } catch (fe: FormulaError) {
                SheetValue.ErrorVal(fe.code)
            } catch (_: Throwable) {
                SheetValue.ErrorVal("#VALUE!")
            } finally {
                visiting.remove(key)
            }
            cache[key] = EvaluatedCell(cellInput.raw, result)
            return result
        }

        val output = mutableMapOf<String, Map<String, EvaluatedCell>>()
        for (sheet in sheets) {
            val evaluatedMap = LinkedHashMap<String, EvaluatedCell>(sheet.cells.size)
            for ((addr, input) in sheet.cells) {
                val normAddr = addr.uppercase()
                val v = evalCell(sheet, normAddr)
                evaluatedMap[normAddr] = EvaluatedCell(input.raw, v)
            }
            output[sheet.id] = evaluatedMap
        }
        return output
    }

    fun colLabelToIndex(label: String): Int {
        var idx = 0
        for (ch in label.uppercase()) {
            idx = idx * 26 + (ch - 'A' + 1)
        }
        return idx - 1
    }

    fun indexToColLabel(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A' + rem))
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    private fun parseCellCoords(addr: String): Pair<Int, Int>? {
        val clean = addr.replace("$", "").uppercase()
        var i = 0
        while (i < clean.length && clean[i] in 'A'..'Z') i++
        if (i == 0 || i == clean.length) return null
        val col = colLabelToIndex(clean.substring(0, i))
        val row = clean.substring(i).toIntOrNull() ?: return null
        if (row < 1) return null
        return col to row
    }

    private sealed interface FTok {
        data class Num(val value: Double) : FTok
        data class Str(val value: String) : FTok
        data class RefOrIdent(val text: String) : FTok
        data class Op(val sym: String) : FTok
        data object LParen : FTok
        data object RParen : FTok
        data object Comma : FTok
        data object Colon : FTok
        data object Bang : FTok
        data object Eof : FTok
    }

    private class FormulaParser(
        private val expr: String,
        private val currentSheet: WorksheetModel,
        private val todayIso: String,
        private val resolveSheet: (String?) -> WorksheetModel,
        private val evalCell: (WorksheetModel, String) -> SheetValue,
    ) {
        private val tokens = tokenize(expr)
        private var pos = 0

        private fun cur(): FTok = tokens[pos]
        private fun peek(offset: Int): FTok = tokens.getOrElse(pos + offset) { FTok.Eof }
        private fun next(): FTok = tokens[pos++]

        fun evaluate(): SheetValue {
            val v = parseComparison()
            if (cur() != FTok.Eof) throw FormulaError("#VALUE!")
            return v
        }

        private fun parseComparison(): SheetValue {
            var left = parseConcat()
            while (true) {
                val tok = cur()
                if (tok is FTok.Op && tok.sym in setOf("=", "<>", "!=", "<", "<=", ">", ">=")) {
                    next()
                    val right = parseConcat()
                    left = compareValues(left, right, tok.sym)
                } else break
            }
            return left
        }

        private fun parseConcat(): SheetValue {
            var left = parseAddSub()
            while (cur() == FTok.Op("&")) {
                next()
                val right = parseAddSub()
                propagateError(left, right)?.let { return it }
                left = SheetValue.TextVal(left.displayString() + right.displayString())
            }
            return left
        }

        private fun parseAddSub(): SheetValue {
            var left = parseMulDiv()
            while (true) {
                val tok = cur()
                if (tok is FTok.Op && (tok.sym == "+" || tok.sym == "-")) {
                    next()
                    val right = parseMulDiv()
                    propagateError(left, right)?.let { return it }
                    val a = toDouble(left)
                    val b = toDouble(right)
                    left = SheetValue.NumberVal(if (tok.sym == "+") a + b else a - b)
                } else break
            }
            return left
        }

        private fun parseMulDiv(): SheetValue {
            var left = parseUnary()
            while (true) {
                val tok = cur()
                if (tok is FTok.Op && (tok.sym == "*" || tok.sym == "/")) {
                    next()
                    val right = parseUnary()
                    propagateError(left, right)?.let { return it }
                    val a = toDouble(left)
                    val b = toDouble(right)
                    if (tok.sym == "/") {
                        if (b == 0.0) throw FormulaError("#DIV/0!")
                        left = SheetValue.NumberVal(a / b)
                    } else {
                        left = SheetValue.NumberVal(a * b)
                    }
                } else break
            }
            return left
        }

        private fun parseUnary(): SheetValue {
            val tok = cur()
            if (tok is FTok.Op && tok.sym == "-") {
                next()
                val v = parseUnary()
                if (v is SheetValue.ErrorVal) return v
                return SheetValue.NumberVal(-toDouble(v))
            }
            if (tok is FTok.Op && tok.sym == "+") {
                next()
                return parseUnary()
            }
            return parsePower()
        }

        private fun parsePower(): SheetValue {
            val base = parsePrimary()
            if (cur() == FTok.Op("^")) {
                next()
                val exp = parseUnary()
                propagateError(base, exp)?.let { return it }
                return SheetValue.NumberVal(toDouble(base).pow(toDouble(exp)))
            }
            return base
        }

        private fun parsePrimary(): SheetValue {
            return when (val tok = cur()) {
                is FTok.Num -> { next(); SheetValue.NumberVal(tok.value) }
                is FTok.Str -> { next(); SheetValue.TextVal(tok.value) }
                FTok.LParen -> {
                    next()
                    val inner = parseComparison()
                    if (cur() != FTok.RParen) throw FormulaError("#VALUE!")
                    next()
                    inner
                }
                is FTok.RefOrIdent -> {
                    // Check for function call
                    if (peek(1) == FTok.LParen) {
                        val fnName = tok.text.uppercase()
                        next() // ident
                        next() // '('
                        return parseFunctionCall(fnName)
                    }
                    // Check for explicit sheet reference: SheetName!A1
                    if (peek(1) == FTok.Bang) {
                        val sheetName = tok.text
                        next() // sheet
                        next() // '!'
                        val target = next() as? FTok.RefOrIdent ?: throw FormulaError("#REF!")
                        val targetSheet = resolveSheet(sheetName)
                        return evalCell(targetSheet, target.text)
                    }
                    next()
                    val text = tok.text
                    when {
                        text.equals("TRUE", ignoreCase = true) -> SheetValue.BoolVal(true)
                        text.equals("FALSE", ignoreCase = true) -> SheetValue.BoolVal(false)
                        currentSheet.namedRanges.containsKey(text) -> {
                            val rangeExpr = currentSheet.namedRanges[text]!!
                            val vals = resolveRangeExpr(currentSheet, rangeExpr)
                            vals.firstOrNull() ?: SheetValue.EmptyVal
                        }
                        parseCellCoords(text) != null -> evalCell(currentSheet, text)
                        else -> throw FormulaError("#NAME?")
                    }
                }
                else -> throw FormulaError("#VALUE!")
            }
        }

        private sealed interface ArgItem {
            data class Single(val value: SheetValue) : ArgItem
            data class Range(val values: List<SheetValue>) : ArgItem
        }

        private fun parseFunctionArgs(): List<ArgItem> {
            val args = mutableListOf<ArgItem>()
            if (cur() == FTok.RParen) {
                next()
                return args
            }
            while (true) {
                args.add(parseSingleOrRangeArg())
                if (cur() == FTok.Comma) {
                    next()
                } else {
                    if (cur() != FTok.RParen) throw FormulaError("#VALUE!")
                    next()
                    break
                }
            }
            return args
        }

        private fun parseSingleOrRangeArg(): ArgItem {
            // Detect Sheet!A1:B3 or A1:B3 or NamedRange
            val t0 = cur()
            if (t0 is FTok.RefOrIdent) {
                if (peek(1) == FTok.Bang && peek(3) == FTok.Colon) {
                    val sheetName = t0.text
                    next(); next()
                    val startRef = (next() as FTok.RefOrIdent).text
                    next() // ':'
                    val endRef = (next() as FTok.RefOrIdent).text
                    val targetSheet = resolveSheet(sheetName)
                    return ArgItem.Range(expandRange(targetSheet, startRef, endRef))
                }
                if (peek(1) == FTok.Colon) {
                    val startRef = t0.text
                    next(); next()
                    val endRef = (next() as FTok.RefOrIdent).text
                    return ArgItem.Range(expandRange(currentSheet, startRef, endRef))
                }
                if (currentSheet.namedRanges.containsKey(t0.text) && (peek(1) == FTok.Comma || peek(1) == FTok.RParen)) {
                    next()
                    return ArgItem.Range(resolveRangeExpr(currentSheet, currentSheet.namedRanges[t0.text]!!))
                }
            }
            return ArgItem.Single(parseComparison())
        }

        private fun resolveRangeExpr(sheet: WorksheetModel, rawRange: String): List<SheetValue> {
            val bangIdx = rawRange.indexOf('!')
            val (targetSheet, rangePart) = if (bangIdx >= 0) {
                resolveSheet(rawRange.substring(0, bangIdx)) to rawRange.substring(bangIdx + 1)
            } else {
                sheet to rawRange
            }
            val colonIdx = rangePart.indexOf(':')
            return if (colonIdx >= 0) {
                expandRange(targetSheet, rangePart.substring(0, colonIdx), rangePart.substring(colonIdx + 1))
            } else {
                listOf(evalCell(targetSheet, rangePart))
            }
        }

        private fun expandRange(sheet: WorksheetModel, startAddr: String, endAddr: String): List<SheetValue> {
            val (c1, r1) = parseCellCoords(startAddr) ?: throw FormulaError("#REF!")
            val (c2, r2) = parseCellCoords(endAddr) ?: throw FormulaError("#REF!")
            val out = mutableListOf<SheetValue>()
            for (r in min(r1, r2)..max(r1, r2)) {
                for (c in min(c1, c2)..max(c1, c2)) {
                    val addr = "${indexToColLabel(c)}$r"
                    val v = evalCell(sheet, addr)
                    if (v is SheetValue.ErrorVal) throw FormulaError(v.code)
                    out.add(v)
                }
            }
            return out
        }

        private fun flattenValues(args: List<ArgItem>): List<SheetValue> = args.flatMap {
            when (it) {
                is ArgItem.Single -> listOf(it.value)
                is ArgItem.Range -> it.values
            }
        }

        private fun flattenNumbers(args: List<ArgItem>): List<Double> {
            val vals = flattenValues(args)
            val nums = mutableListOf<Double>()
            for (v in vals) {
                when (v) {
                    is SheetValue.ErrorVal -> throw FormulaError(v.code)
                    is SheetValue.NumberVal -> nums.add(v.number)
                    else -> Unit
                }
            }
            return nums
        }

        private fun singleVal(arg: ArgItem): SheetValue = when (arg) {
            is ArgItem.Single -> arg.value
            is ArgItem.Range -> arg.values.firstOrNull() ?: SheetValue.EmptyVal
        }

        private fun parseFunctionCall(name: String): SheetValue {
            val args = parseFunctionArgs()
            return when (name) {
                "SUM" -> SheetValue.NumberVal(flattenNumbers(args).sum())
                "AVERAGE", "AVG" -> {
                    val ns = flattenNumbers(args)
                    if (ns.isEmpty()) throw FormulaError("#DIV/0!")
                    SheetValue.NumberVal(ns.sum() / ns.size)
                }
                "COUNT" -> SheetValue.NumberVal(flattenNumbers(args).size.toDouble())
                "COUNTA" -> {
                    val count = flattenValues(args).count { it != SheetValue.EmptyVal && it.displayString().isNotEmpty() }
                    SheetValue.NumberVal(count.toDouble())
                }
                "MIN" -> {
                    val ns = flattenNumbers(args)
                    SheetValue.NumberVal(ns.minOrNull() ?: 0.0)
                }
                "MAX" -> {
                    val ns = flattenNumbers(args)
                    SheetValue.NumberVal(ns.maxOrNull() ?: 0.0)
                }
                "MEDIAN" -> {
                    val ns = flattenNumbers(args).sorted()
                    if (ns.isEmpty()) throw FormulaError("#NUM!")
                    val mid = ns.size / 2
                    val med = if (ns.size % 2 == 1) ns[mid] else (ns[mid - 1] + ns[mid]) / 2.0
                    SheetValue.NumberVal(med)
                }
                "STDEV", "STDEV_S", "STDEV.S" -> {
                    val ns = flattenNumbers(args)
                    if (ns.size < 2) throw FormulaError("#DIV/0!")
                    val mean = ns.sum() / ns.size
                    val sd = sqrt(ns.sumOf { (it - mean) * (it - mean) } / (ns.size - 1))
                    SheetValue.NumberVal(sd)
                }
                "COUNTIF" -> {
                    val rangeVals = (args.getOrNull(0) as? ArgItem.Range)?.values ?: listOf(singleVal(args[0]))
                    val crit = singleVal(args[1]).displayString()
                    SheetValue.NumberVal(rangeVals.count { matchesCriterion(it, crit) }.toDouble())
                }
                "SUMIF" -> {
                    val rangeVals = (args[0] as? ArgItem.Range)?.values ?: listOf(singleVal(args[0]))
                    val crit = singleVal(args[1]).displayString()
                    val sumVals = if (args.size >= 3) {
                        (args[2] as? ArgItem.Range)?.values ?: listOf(singleVal(args[2]))
                    } else rangeVals
                    var total = 0.0
                    for (i in rangeVals.indices) {
                        if (matchesCriterion(rangeVals[i], crit)) {
                            val sv = sumVals.getOrNull(i)
                            if (sv is SheetValue.NumberVal) total += sv.number
                        }
                    }
                    SheetValue.NumberVal(total)
                }
                "IF" -> {
                    val cond = toBool(singleVal(args[0]))
                    if (cond) singleVal(args[1]) else args.getOrNull(2)?.let(::singleVal) ?: SheetValue.BoolVal(false)
                }
                "AND" -> SheetValue.BoolVal(flattenValues(args).all(::toBool))
                "OR" -> SheetValue.BoolVal(flattenValues(args).any(::toBool))
                "NOT" -> SheetValue.BoolVal(!toBool(singleVal(args[0])))
                "XLOOKUP" -> {
                    val lookup = singleVal(args[0])
                    val lookupRange = (args[1] as? ArgItem.Range)?.values ?: listOf(singleVal(args[1]))
                    val returnRange = (args[2] as? ArgItem.Range)?.values ?: listOf(singleVal(args[2]))
                    val notFound = args.getOrNull(3)?.let(::singleVal) ?: SheetValue.ErrorVal("#N/A")
                    val idx = lookupRange.indexOfFirst { valuesEqual(it, lookup) }
                    if (idx >= 0 && idx < returnRange.size) returnRange[idx] else notFound
                }
                "CONCAT", "CONCATENATE" -> {
                    val sb = StringBuilder()
                    for (v in flattenValues(args)) {
                        if (v is SheetValue.ErrorVal) return v
                        sb.append(v.displayString())
                    }
                    SheetValue.TextVal(sb.toString())
                }
                "UPPER" -> SheetValue.TextVal(singleVal(args[0]).displayString().uppercase())
                "LOWER" -> SheetValue.TextVal(singleVal(args[0]).displayString().lowercase())
                "LEN" -> SheetValue.NumberVal(singleVal(args[0]).displayString().length.toDouble())
                "LEFT" -> {
                    val s = singleVal(args[0]).displayString()
                    val count = if (args.size >= 2) toDouble(singleVal(args[1])).toInt() else 1
                    if (count < 0) throw FormulaError("#VALUE!")
                    SheetValue.TextVal(s.take(count)) // Exact zero-length semantics (CONT-007)
                }
                "RIGHT" -> {
                    val s = singleVal(args[0]).displayString()
                    val count = if (args.size >= 2) toDouble(singleVal(args[1])).toInt() else 1
                    if (count < 0) throw FormulaError("#VALUE!")
                    SheetValue.TextVal(s.takeLast(count)) // Exact zero-length semantics (CONT-007)
                }
                "PMT" -> {
                    val rate = toDouble(singleVal(args[0]))
                    val nper = toDouble(singleVal(args[1]))
                    val pv = toDouble(singleVal(args[2]))
                    val fv = args.getOrNull(3)?.let { toDouble(singleVal(it)) } ?: 0.0
                    val pmt = if (rate == 0.0) {
                        -(pv + fv) / nper
                    } else {
                        val pvif = (1.0 + rate).pow(nper)
                        -rate * (pv * pvif + fv) / (pvif - 1.0)
                    }
                    SheetValue.NumberVal(pmt)
                }
                "NPV" -> {
                    val rate = toDouble(singleVal(args[0]))
                    val flows = flattenNumbers(args.drop(1))
                    var npv = 0.0
                    for (i in flows.indices) {
                        npv += flows[i] / (1.0 + rate).pow(i + 1)
                    }
                    SheetValue.NumberVal(npv)
                }
                "ROUND" -> {
                    val v = toDouble(singleVal(args[0]))
                    val d = args.getOrNull(1)?.let { toDouble(singleVal(it)).toInt() } ?: 0
                    val f = 10.0.pow(d)
                    SheetValue.NumberVal(round(v * f) / f)
                }
                "ABS" -> SheetValue.NumberVal(abs(toDouble(singleVal(args[0]))))
                "SQRT" -> {
                    val v = toDouble(singleVal(args[0]))
                    if (v < 0.0) throw FormulaError("#NUM!")
                    SheetValue.NumberVal(sqrt(v))
                }
                "TODAY" -> SheetValue.TextVal(todayIso)
                else -> throw FormulaError("#NAME?")
            }
        }

        private fun matchesCriterion(value: SheetValue, criterion: String): Boolean {
            val c = criterion.trim()
            val ops = listOf(">=", "<=", "<>", "!=", ">", "<", "=")
            for (op in ops) {
                if (c.startsWith(op)) {
                    val rhs = c.substring(op.length).trim()
                    val rNum = rhs.toDoubleOrNull()
                    if (value is SheetValue.NumberVal && rNum != null) {
                        return when (op) {
                            ">=" -> value.number >= rNum
                            "<=" -> value.number <= rNum
                            "<>", "!=" -> value.number != rNum
                            ">" -> value.number > rNum
                            "<" -> value.number < rNum
                            "=" -> value.number == rNum
                            else -> false
                        }
                    }
                    val cmp = value.displayString().compareTo(rhs, ignoreCase = true)
                    return when (op) {
                        "=" -> cmp == 0
                        "<>", "!=" -> cmp != 0
                        else -> false
                    }
                }
            }
            val cNum = c.toDoubleOrNull()
            if (value is SheetValue.NumberVal && cNum != null) return value.number == cNum
            return value.displayString().equals(c, ignoreCase = true)
        }

        private fun valuesEqual(a: SheetValue, b: SheetValue): Boolean {
            if (a is SheetValue.NumberVal && b is SheetValue.NumberVal) return abs(a.number - b.number) < 1e-12
            return a.displayString().equals(b.displayString(), ignoreCase = true)
        }

        private fun compareValues(a: SheetValue, b: SheetValue, op: String): SheetValue {
            propagateError(a, b)?.let { return it }
            val cmp = if (a is SheetValue.NumberVal && b is SheetValue.NumberVal) {
                a.number.compareTo(b.number)
            } else {
                a.displayString().compareTo(b.displayString(), ignoreCase = true)
            }
            val flag = when (op) {
                "=" -> cmp == 0
                "<>", "!=" -> cmp != 0
                "<" -> cmp < 0
                "<=" -> cmp <= 0
                ">" -> cmp > 0
                ">=" -> cmp >= 0
                else -> false
            }
            return SheetValue.BoolVal(flag)
        }

        private fun propagateError(a: SheetValue, b: SheetValue): SheetValue.ErrorVal? =
            (a as? SheetValue.ErrorVal) ?: (b as? SheetValue.ErrorVal)

        private fun toDouble(v: SheetValue): Double = when (v) {
            is SheetValue.NumberVal -> v.number
            is SheetValue.BoolVal -> if (v.flag) 1.0 else 0.0
            SheetValue.EmptyVal -> 0.0
            is SheetValue.TextVal -> v.text.toDoubleOrNull() ?: throw FormulaError("#VALUE!")
            is SheetValue.ErrorVal -> throw FormulaError(v.code)
        }

        private fun toBool(v: SheetValue): Boolean = when (v) {
            is SheetValue.BoolVal -> v.flag
            is SheetValue.NumberVal -> v.number != 0.0
            is SheetValue.TextVal -> v.text.equals("TRUE", ignoreCase = true)
            SheetValue.EmptyVal -> false
            is SheetValue.ErrorVal -> throw FormulaError(v.code)
        }

        private fun tokenize(raw: String): List<FTok> {
            val out = mutableListOf<FTok>()
            var i = 0
            while (i < raw.length) {
                val ch = raw[i]
                when {
                    ch.isWhitespace() -> i++
                    ch == '"' -> {
                        i++
                        val sb = StringBuilder()
                        while (i < raw.length) {
                            if (raw[i] == '"' && i + 1 < raw.length && raw[i + 1] == '"') {
                                sb.append('"'); i += 2
                            } else if (raw[i] == '"') {
                                i++; break
                            } else {
                                sb.append(raw[i]); i++
                            }
                        }
                        out.add(FTok.Str(sb.toString()))
                    }
                    ch == '\'' -> {
                        // Quoted sheet name e.g. 'Lab Sheet'!A1
                        i++
                        val sb = StringBuilder()
                        while (i < raw.length && raw[i] != '\'') {
                            sb.append(raw[i]); i++
                        }
                        if (i < raw.length) i++
                        out.add(FTok.RefOrIdent(sb.toString()))
                    }
                    ch.isDigit() || (ch == '.' && i + 1 < raw.length && raw[i + 1].isDigit()) -> {
                        val start = i
                        while (i < raw.length && (raw[i].isDigit() || raw[i] == '.')) i++
                        if (i < raw.length && (raw[i] == 'e' || raw[i] == 'E')) {
                            val ep = i; i++
                            if (i < raw.length && (raw[i] == '+' || raw[i] == '-')) i++
                            val ds = i
                            while (i < raw.length && raw[i].isDigit()) i++
                            if (i == ds) i = ep
                        }
                        out.add(FTok.Num(raw.substring(start, i).toDouble()))
                    }
                    ch.isLetter() || ch == '_' || ch == '$' -> {
                        val start = i
                        while (i < raw.length && (raw[i].isLetterOrDigit() || raw[i] == '_' || raw[i] == '$' || raw[i] == '.')) i++
                        out.add(FTok.RefOrIdent(raw.substring(start, i)))
                    }
                    ch == '<' && i + 1 < raw.length && raw[i + 1] in setOf('=', '>') -> {
                        out.add(FTok.Op(raw.substring(i, i + 2))); i += 2
                    }
                    ch == '>' && i + 1 < raw.length && raw[i + 1] == '=' -> {
                        out.add(FTok.Op(">=")); i += 2
                    }
                    ch == '!' && i + 1 < raw.length && raw[i + 1] == '=' -> {
                        out.add(FTok.Op("!=")); i += 2
                    }
                    ch in "+-*/^&=<>".toSet() -> { out.add(FTok.Op(ch.toString())); i++ }
                    ch == '(' -> { out.add(FTok.LParen); i++ }
                    ch == ')' -> { out.add(FTok.RParen); i++ }
                    ch == ',' -> { out.add(FTok.Comma); i++ }
                    ch == ':' -> { out.add(FTok.Colon); i++ }
                    ch == '!' -> { out.add(FTok.Bang); i++ }
                    else -> throw FormulaError("#VALUE!")
                }
            }
            out.add(FTok.Eof)
            return out
        }
    }
}
