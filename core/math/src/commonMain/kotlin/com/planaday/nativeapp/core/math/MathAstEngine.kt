package com.planaday.nativeapp.core.math

import kotlin.math.*

enum class AngleMode { RAD, DEG, GRAD }

data class UserDefinedFunction(val parameter: String, val bodyExpression: String)

data class MathEvalContext(
    val angleMode: AngleMode = AngleMode.RAD,
    val variables: Map<String, Double> = emptyMap(),
    val userFunctions: Map<String, UserDefinedFunction> = emptyMap(),
    val maxCallDepth: Int = 32,
)

data class ComplexNumber(val re: Double, val im: Double) {
    operator fun plus(other: ComplexNumber) = ComplexNumber(re + other.re, im + other.im)
    operator fun minus(other: ComplexNumber) = ComplexNumber(re - other.re, im - other.im)
    operator fun times(other: ComplexNumber) = ComplexNumber(
        re * other.re - im * other.im,
        re * other.im + im * other.re,
    )
    operator fun div(other: ComplexNumber): ComplexNumber {
        val denom = other.re * other.re + other.im * other.im
        require(denom != 0.0) { "Complex division by zero" }
        return ComplexNumber(
            (re * other.re + im * other.im) / denom,
            (im * other.re - re * other.im) / denom,
        )
    }
    fun magnitude(): Double = hypot(re, im)
}

class MathSyntaxException(message: String) : IllegalArgumentException(message)

/**
 * Clean-room recursive-descent AST mathematical expression parser and evaluator.
 * Supports operator precedence (right-associative ^, unary -, postfix !, implicit multiplication like 2pi),
 * trig/hyperbolic/log/combinatorial/statistical functions, angle modes, user variables/functions,
 * numerical calculus, continuity-validated root finding (PD-007), complex arithmetic, and matrices.
 * Zero eval() or dynamic script execution.
 */
object MathAstEngine {
    private sealed interface Token {
        data class Num(val value: Double) : Token
        data class Ident(val name: String) : Token
        data class Op(val symbol: Char) : Token
        data object LParen : Token
        data object RParen : Token
        data object Comma : Token
        data object Bang : Token
        data object Eof : Token
    }

    private sealed interface Expr {
        data class Literal(val value: Double) : Expr
        data class Variable(val name: String) : Expr
        data class UnaryNeg(val operand: Expr) : Expr
        data class PostfixFact(val operand: Expr) : Expr
        data class Binary(val op: Char, val left: Expr, val right: Expr) : Expr
        data class Call(val name: String, val args: List<Expr>) : Expr
    }

    fun evaluate(expression: String, ctx: MathEvalContext = MathEvalContext()): Double {
        val tokens = tokenize(expression)
        val parser = Parser(tokens)
        val ast = parser.parseExpression()
        if (parser.current() != Token.Eof) {
            throw MathSyntaxException("Unexpected trailing token in expression: '$expression'")
        }
        return evalAst(ast, ctx, depth = 0)
    }

    fun factorial(x: Double): Double {
        if (x.isNaN() || x.isInfinite()) return Double.NaN
        if (x < 0.0 && x == floor(x)) return Double.NaN
        if (x >= 0.0 && x <= 170.0 && x == floor(x)) {
            var acc = 1.0
            var i = 2
            val n = x.toInt()
            while (i <= n) {
                acc *= i
                i++
            }
            return acc
        }
        return lanczosGamma(x + 1.0)
    }

    private fun lanczosGamma(zInput: Double): Double {
        val coeffs = doubleArrayOf(
            676.5203681218851,
            -1259.1392167224028,
            771.32342877765313,
            -176.61502916214059,
            12.507343278686905,
            -0.13857109526572012,
            9.9843695780195716e-6,
            1.5056327351493116e-7,
        )
        if (zInput < 0.5) {
            return PI / (sin(PI * zInput) * lanczosGamma(1.0 - zInput))
        }
        val z = zInput - 1.0
        var sum = 0.99999999999980993
        for (i in coeffs.indices) {
            sum += coeffs[i] / (z + i + 1.0)
        }
        val t = z + coeffs.size - 0.5
        return sqrt(2.0 * PI) * t.pow(z + 0.5) * exp(-t) * sum
    }

    fun numericalDerivative(
        expression: String,
        x: Double,
        ctx: MathEvalContext = MathEvalContext(),
        h: Double = 1e-5,
    ): Double {
        val fPlus = evaluate(expression, ctx.copy(variables = ctx.variables + ("x" to (x + h))))
        val fMinus = evaluate(expression, ctx.copy(variables = ctx.variables + ("x" to (x - h))))
        val fPlus2 = evaluate(expression, ctx.copy(variables = ctx.variables + ("x" to (x + 2 * h))))
        val fMinus2 = evaluate(expression, ctx.copy(variables = ctx.variables + ("x" to (x - 2 * h))))
        return (-fPlus2 + 8.0 * fPlus - 8.0 * fMinus + fMinus2) / (12.0 * h)
    }

    fun numericalIntegral(
        expression: String,
        lower: Double,
        upper: Double,
        ctx: MathEvalContext = MathEvalContext(),
        intervals: Int = 400,
    ): Double {
        if (lower == upper) return 0.0
        val n = if (intervals % 2 == 0) intervals else intervals + 1
        val step = (upper - lower) / n
        fun f(v: Double): Double = evaluate(expression, ctx.copy(variables = ctx.variables + ("x" to v)))

        val fLower = f(lower)
        val fUpper = f(upper)
        if (!fLower.isFinite() || !fUpper.isFinite()) return Double.POSITIVE_INFINITY

        var sum = fLower + fUpper
        for (i in 1 until n) {
            val xi = lower + i * step
            val yi = f(xi)
            if (!yi.isFinite() || abs(yi) > 1e12) return Double.POSITIVE_INFINITY
            sum += if (i % 2 == 0) 2.0 * yi else 4.0 * yi
        }
        return sum * step / 3.0
    }

    /**
     * Continuity-validated root finder (closes PD-007):
     * 1. Rejects poles/discontinuities such as 1/(x - 0.123) via residual and neighborhood magnitude verification.
     * 2. Detects even-multiplicity tangent roots such as (x - 0.123)^2 via golden-section minimization on local |f(x)| minima.
     */
    fun findNumericalRoots(
        expression: String,
        minX: Double,
        maxX: Double,
        ctx: MathEvalContext = MathEvalContext(),
        samples: Int = 800,
    ): List<Double> {
        require(maxX > minX) { "maxX must exceed minX" }
        val ast = parseCached(expression)
        fun evalAt(v: Double): Double = evalAst(ast, ctx.copy(variables = ctx.variables + ("x" to v)), 0)

        val step = (maxX - minX) / samples
        val xs = DoubleArray(samples + 1) { i -> minX + i * step }
        val ys = DoubleArray(samples + 1) { i -> evalAt(xs[i]) }
        val candidates = mutableListOf<Double>()

        // Pass 1: exact hits and sign-change bisection with pole rejection
        for (i in 0..samples) {
            val y = ys[i]
            if (y.isFinite() && abs(y) < 1e-9) {
                candidates.add(xs[i])
            }
            if (i < samples) {
                val y0 = ys[i]
                val y1 = ys[i + 1]
                if (y0.isFinite() && y1.isFinite() && y0 * y1 < 0.0) {
                    val root = bisectRoot(::evalAt, xs[i], xs[i + 1], y0)
                    if (root != null && verifyContinuousZero(::evalAt, root, step)) {
                        candidates.add(root)
                    }
                }
            }
        }

        // Pass 2: tangent (even-multiplicity) roots via local |f(x)| minima + golden-section refinement
        val phi = (sqrt(5.0) - 1.0) / 2.0
        for (i in 1 until samples) {
            val yl = abs(ys[i - 1])
            val ym = abs(ys[i])
            val yr = abs(ys[i + 1])
            if (yl.isFinite() && ym.isFinite() && yr.isFinite() && ym <= yl && ym <= yr) {
                var a = xs[i - 1]
                var b = xs[i + 1]
                var c = b - phi * (b - a)
                var d = a + phi * (b - a)
                var fc = abs(evalAt(c))
                var fd = abs(evalAt(d))
                repeat(55) {
                    if (fc < fd) {
                        b = d
                        d = c
                        fd = fc
                        c = b - phi * (b - a)
                        fc = abs(evalAt(c))
                    } else {
                        a = c
                        c = d
                        fc = fd
                        d = a + phi * (b - a)
                        fd = abs(evalAt(d))
                    }
                }
                val bestX = (a + b) / 2.0
                val bestY = abs(evalAt(bestX))
                if (bestY.isFinite() && bestY < 1e-6 && verifyContinuousZero(::evalAt, bestX, step)) {
                    candidates.add(bestX)
                }
            }
        }

        // Deduplicate & snap near-exact integers/clean values
        val sorted = candidates.sorted()
        val deduped = mutableListOf<Double>()
        for (r in sorted) {
            if (deduped.isEmpty() || abs(r - deduped.last()) > 1e-4) {
                val snapped = if (abs(r - r.roundToInt().toDouble()) < 1e-7) r.roundToInt().toDouble() else r
                deduped.add(if (snapped == -0.0) 0.0 else snapped)
            }
        }
        return deduped
    }

    private fun bisectRoot(f: (Double) -> Double, leftIn: Double, rightIn: Double, fLeftIn: Double): Double? {
        var lo = leftIn
        var hi = rightIn
        var fLo = fLeftIn
        repeat(70) {
            val mid = 0.5 * (lo + hi)
            val fMid = f(mid)
            if (!fMid.isFinite()) return null
            if (abs(fMid) < 1e-12) return mid
            if (fLo * fMid < 0.0) {
                hi = mid
            } else {
                lo = mid
                fLo = fMid
            }
        }
        return 0.5 * (lo + hi)
    }

    private fun verifyContinuousZero(f: (Double) -> Double, root: Double, sampleStep: Double): Boolean {
        val fRoot = f(root)
        if (!fRoot.isFinite() || abs(fRoot) > 1e-4) return false
        val eps = max(1e-6, sampleStep * 0.05)
        val left = f(root - eps)
        val right = f(root + eps)
        if (!left.isFinite() || !right.isFinite()) return false
        // Reject poles where |f(root +/- eps)| explodes while |f(root)| appeared small
        if (abs(left) > 1e3 || abs(right) > 1e3) return false
        val tighterLeft = f(root - eps * 0.1)
        val tighterRight = f(root + eps * 0.1)
        if (!tighterLeft.isFinite() || !tighterRight.isFinite()) return false
        if (abs(tighterLeft) > abs(left) * 2.0 + 1e-4 && abs(tighterLeft) > 1.0) return false
        if (abs(tighterRight) > abs(right) * 2.0 + 1e-4 && abs(tighterRight) > 1.0) return false
        return true
    }

    fun matrixDeterminant(matrix: List<List<Double>>): Double {
        val n = matrix.size
        require(n > 0 && matrix.all { it.size == n }) { "Matrix must be non-empty square" }
        val a = Array(n) { r -> DoubleArray(n) { c -> matrix[r][c] } }
        var det = 1.0
        for (i in 0 until n) {
            var pivotRow = i
            var maxVal = abs(a[i][i])
            for (r in i + 1 until n) {
                val v = abs(a[r][i])
                if (v > maxVal) {
                    maxVal = v
                    pivotRow = r
                }
            }
            if (maxVal < 1e-15) return 0.0
            if (pivotRow != i) {
                val tmp = a[i]
                a[i] = a[pivotRow]
                a[pivotRow] = tmp
                det = -det
            }
            det *= a[i][i]
            for (r in i + 1 until n) {
                val factor = a[r][i] / a[i][i]
                for (c in i until n) {
                    a[r][c] -= factor * a[i][c]
                }
            }
        }
        return if (abs(det - det.roundToLong().toDouble()) < 1e-9) det.roundToLong().toDouble() else det
    }

    fun formatSignificantFigures(value: Double, sigFigs: Int): String {
        require(sigFigs in 1..15) { "sigFigs must be 1..15" }
        if (!value.isFinite()) return value.toString()
        if (value == 0.0) return "0"
        val magnitude = floor(log10(abs(value))).toInt()
        val decimals = (sigFigs - 1 - magnitude).coerceAtLeast(0)
        val factor = 10.0.pow(decimals)
        val rounded = round(value * factor) / factor
        return formatFixed(rounded, decimals)
    }

    fun toEngineeringNotation(value: Double, decimals: Int = 3): String {
        if (!value.isFinite() || value == 0.0) return formatFixed(value, decimals)
        val rawExp = floor(log10(abs(value))).toInt()
        val engExp = Math.floorDiv(rawExp, 3) * 3
        val mantissa = value / 10.0.pow(engExp)
        val mantStr = formatFixed(mantissa, decimals)
        return if (engExp == 0) mantStr else "${mantStr}×10^$engExp"
    }

    private fun formatFixed(value: Double, decimals: Int): String {
        if (decimals <= 0) return round(value).toLong().toString()
        val neg = value < 0.0
        val absVal = abs(value)
        val scale = 10.0.pow(decimals)
        val scaled = round(absVal * scale).toLong()
        val intPart = scaled / scale.toLong()
        val fracPart = (scaled % scale.toLong()).toString().padStart(decimals, '0')
        return "${if (neg) "-" else ""}$intPart.$fracPart"
    }

    private fun parseCached(expression: String): Expr {
        val tokens = tokenize(expression)
        val parser = Parser(tokens)
        val expr = parser.parseExpression()
        if (parser.current() != Token.Eof) throw MathSyntaxException("Trailing tokens in '$expression'")
        return expr
    }

    private fun tokenize(raw: String): List<Token> {
        val s = raw.trim()
        if (s.isEmpty()) throw MathSyntaxException("Empty expression")
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when {
                ch.isWhitespace() -> i++
                ch.isDigit() || (ch == '.' && i + 1 < s.length && s[i + 1].isDigit()) -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                    if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                        val expPos = i
                        i++
                        if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                        val digitsStart = i
                        while (i < s.length && s[i].isDigit()) i++
                        if (i == digitsStart) i = expPos
                    }
                    val numStr = s.substring(start, i)
                    val num = numStr.toDoubleOrNull() ?: throw MathSyntaxException("Invalid number '$numStr'")
                    tokens.add(Token.Num(num))
                }
                ch.isLetter() || ch == '_' -> {
                    val start = i
                    while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
                    tokens.add(Token.Ident(s.substring(start, i)))
                }
                ch == '+' || ch == '-' || ch == '*' || ch == '/' || ch == '%' || ch == '^' -> {
                    tokens.add(Token.Op(ch)); i++
                }
                ch == '×' -> { tokens.add(Token.Op('*')); i++ }
                ch == '÷' -> { tokens.add(Token.Op('/')); i++ }
                ch == '−' -> { tokens.add(Token.Op('-')); i++ }
                ch == '(' -> { tokens.add(Token.LParen); i++ }
                ch == ')' -> { tokens.add(Token.RParen); i++ }
                ch == ',' -> { tokens.add(Token.Comma); i++ }
                ch == '!' -> { tokens.add(Token.Bang); i++ }
                else -> throw MathSyntaxException("Unexpected character '$ch'")
            }
        }
        tokens.add(Token.Eof)
        return tokens
    }

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0
        fun current(): Token = tokens[pos]
        private fun consume(): Token = tokens[pos++]

        fun parseExpression(): Expr = parseAddSub()

        private fun parseAddSub(): Expr {
            var left = parseMulDiv()
            while (true) {
                val tok = current()
                if (tok is Token.Op && (tok.symbol == '+' || tok.symbol == '-')) {
                    consume()
                    left = Expr.Binary(tok.symbol, left, parseMulDiv())
                } else break
            }
            return left
        }

        private fun parseMulDiv(): Expr {
            var left = parseUnary()
            while (true) {
                val tok = current()
                when {
                    tok is Token.Op && (tok.symbol == '*' || tok.symbol == '/' || tok.symbol == '%') -> {
                        consume()
                        left = Expr.Binary(tok.symbol, left, parseUnary())
                    }
                    // Implicit multiplication: e.g. 2pi, 3(x+1), (x+1)(x-1)
                    tok is Token.Ident || tok is Token.LParen -> {
                        left = Expr.Binary('*', left, parseUnary())
                    }
                    else -> break
                }
            }
            return left
        }

        private fun parseUnary(): Expr {
            val tok = current()
            if (tok is Token.Op && tok.symbol == '-') {
                consume()
                // Note: -2^2 binds as -(2^2) = -4 per standard math precedence!
                return Expr.UnaryNeg(parseUnary())
            }
            if (tok is Token.Op && tok.symbol == '+') {
                consume()
                return parseUnary()
            }
            return parsePower()
        }

        private fun parsePower(): Expr {
            var base = parsePostfix()
            val tok = current()
            if (tok is Token.Op && tok.symbol == '^') {
                consume()
                // Right-associative: 2^3^2 = 2^(3^2) = 512
                val exponent = parseUnary()
                base = Expr.Binary('^', base, exponent)
            }
            return base
        }

        private fun parsePostfix(): Expr {
            var node = parsePrimary()
            while (current() == Token.Bang) {
                consume()
                node = Expr.PostfixFact(node)
            }
            return node
        }

        private fun parsePrimary(): Expr {
            return when (val tok = current()) {
                is Token.Num -> { consume(); Expr.Literal(tok.value) }
                is Token.Ident -> {
                    consume()
                    if (current() == Token.LParen) {
                        consume()
                        val args = mutableListOf<Expr>()
                        if (current() != Token.RParen) {
                            args.add(parseExpression())
                            while (current() == Token.Comma) {
                                consume()
                                args.add(parseExpression())
                            }
                        }
                        if (current() != Token.RParen) {
                            throw MathSyntaxException("Missing closing ')' for function '${tok.name}'")
                        }
                        consume()
                        Expr.Call(tok.name, args)
                    } else {
                        Expr.Variable(tok.name)
                    }
                }
                Token.LParen -> {
                    consume()
                    val inner = parseExpression()
                    if (current() != Token.RParen) throw MathSyntaxException("Missing closing ')'")
                    consume()
                    inner
                }
                else -> throw MathSyntaxException("Unexpected token: $tok")
            }
        }
    }

    private fun toRadians(value: Double, mode: AngleMode): Double = when (mode) {
        AngleMode.RAD -> value
        AngleMode.DEG -> value * PI / 180.0
        AngleMode.GRAD -> value * PI / 200.0
    }

    private fun fromRadians(rad: Double, mode: AngleMode): Double = when (mode) {
        AngleMode.RAD -> rad
        AngleMode.DEG -> rad * 180.0 / PI
        AngleMode.GRAD -> rad * 200.0 / PI
    }

    private fun evalAst(expr: Expr, ctx: MathEvalContext, depth: Int): Double {
        if (depth > ctx.maxCallDepth) throw MathSyntaxException("Maximum recursion depth exceeded")
        return when (expr) {
            is Expr.Literal -> expr.value
            is Expr.Variable -> {
                ctx.variables[expr.name]
                    ?: ctx.variables[expr.name.lowercase()]
                    ?: when (expr.name.lowercase()) {
                        "pi" -> PI
                        "e" -> E
                        "tau" -> 2.0 * PI
                        "phi" -> (1.0 + sqrt(5.0)) / 2.0
                        else -> throw MathSyntaxException("Unknown identifier '${expr.name}'")
                    }
            }
            is Expr.UnaryNeg -> -evalAst(expr.operand, ctx, depth)
            is Expr.PostfixFact -> factorial(evalAst(expr.operand, ctx, depth))
            is Expr.Binary -> {
                val l = evalAst(expr.left, ctx, depth)
                val r = evalAst(expr.right, ctx, depth)
                when (expr.op) {
                    '+' -> l + r
                    '-' -> l - r
                    '*' -> l * r
                    '/' -> l / r
                    '%' -> l % r
                    '^' -> l.pow(r)
                    else -> throw MathSyntaxException("Unknown operator ${expr.op}")
                }
            }
            is Expr.Call -> {
                val args = expr.args.map { evalAst(it, ctx, depth) }
                val fn = expr.name.lowercase()
                ctx.userFunctions[expr.name]?.let { ufn ->
                    if (args.size != 1) throw MathSyntaxException("User function '${expr.name}' expects 1 arg")
                    val subCtx = ctx.copy(variables = ctx.variables + (ufn.parameter to args[0]))
                    return evalAst(parseCached(ufn.bodyExpression), subCtx, depth + 1)
                }
                when (fn) {
                    "sin" -> sin(toRadians(args.singleArg(fn), ctx.angleMode))
                    "cos" -> cos(toRadians(args.singleArg(fn), ctx.angleMode))
                    "tan" -> tan(toRadians(args.singleArg(fn), ctx.angleMode))
                    "asin" -> fromRadians(asin(args.singleArg(fn)), ctx.angleMode)
                    "acos" -> fromRadians(acos(args.singleArg(fn)), ctx.angleMode)
                    "atan" -> fromRadians(atan(args.singleArg(fn)), ctx.angleMode)
                    "atan2" -> fromRadians(atan2(args.argAt(0, fn), args.argAt(1, fn)), ctx.angleMode)
                    "sinh" -> sinh(args.singleArg(fn))
                    "cosh" -> cosh(args.singleArg(fn))
                    "tanh" -> tanh(args.singleArg(fn))
                    "asinh" -> asinh(args.singleArg(fn))
                    "acosh" -> acosh(args.singleArg(fn))
                    "atanh" -> atanh(args.singleArg(fn))
                    "sqrt" -> sqrt(args.singleArg(fn))
                    "cbrt" -> cbrt(args.singleArg(fn))
                    "abs" -> abs(args.singleArg(fn))
                    "ln" -> ln(args.singleArg(fn))
                    "log", "log10" -> log10(args.singleArg(fn))
                    "log2" -> log2(args.singleArg(fn))
                    "exp" -> exp(args.singleArg(fn))
                    "pow" -> args.argAt(0, fn).pow(args.argAt(1, fn))
                    "floor" -> floor(args.singleArg(fn))
                    "ceil" -> ceil(args.singleArg(fn))
                    "round" -> {
                        val v = args.argAt(0, fn)
                        if (args.size == 1) round(v)
                        else {
                            val d = args.argAt(1, fn).toInt()
                            val f = 10.0.pow(d)
                            round(v * f) / f
                        }
                    }
                    "sign" -> sign(args.singleArg(fn))
                    "min" -> args.minOrNull() ?: throw MathSyntaxException("min requires args")
                    "max" -> args.maxOrNull() ?: throw MathSyntaxException("max requires args")
                    "sum" -> args.sum()
                    "mean", "avg" -> {
                        if (args.isEmpty()) throw MathSyntaxException("$fn requires args")
                        args.sum() / args.size
                    }
                    "fact", "factorial" -> factorial(args.singleArg(fn))
                    "npr" -> {
                        val n = args.argAt(0, fn)
                        val r = args.argAt(1, fn)
                        factorial(n) / factorial(n - r)
                    }
                    "ncr" -> {
                        val n = args.argAt(0, fn)
                        val r = args.argAt(1, fn)
                        factorial(n) / (factorial(r) * factorial(n - r))
                    }
                    else -> throw MathSyntaxException("Unknown function '${expr.name}'")
                }
            }
        }
    }

    private fun List<Double>.singleArg(name: String): Double {
        if (size != 1) throw MathSyntaxException("Function '$name' expects 1 argument, got $size")
        return this[0]
    }

    private fun List<Double>.argAt(index: Int, name: String): Double {
        if (index >= size) throw MathSyntaxException("Function '$name' missing argument #$index")
        return this[index]
    }
}
