package com.planaday.nativeapp.core.math

import com.planaday.nativeapp.core.model.LabWetNirPair
import com.planaday.nativeapp.core.model.SheetCellInput
import com.planaday.nativeapp.core.model.WorksheetModel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.*

class CoreMathContractTest {
    private fun assertApprox(expected: Double, actual: Double, relTol: Double = 1e-9, label: String = "") {
        val denom = if (abs(expected) > 1e-12) abs(expected) else 1.0
        assertTrue(
            abs(actual - expected) / denom < relTol,
            "$label expected $expected but got $actual",
        )
    }

    @Test
    fun evaluatesAllArithmeticTrigLogCombinatorialAndEdgeReferenceVectors() {
        val cases = listOf(
            "2+3*4" to 14.0,
            "(2+3)*4" to 20.0,
            "-2^2" to -4.0,
            "2^3^2" to 512.0,
            "3/8+1/8" to 0.5,
            "2pi" to 2.0 * PI,
            "sin(pi/6)" to 0.5,
            "cos(pi)" to -1.0,
            "tan(pi/4)" to 1.0,
            "asin(1)" to PI / 2.0,
            "acos(0)" to PI / 2.0,
            "atan(1)" to PI / 4.0,
            "atan2(1,-1)" to 3.0 * PI / 4.0,
            "sinh(0)" to 0.0,
            "cosh(0)" to 1.0,
            "tanh(0)" to 0.0,
            "asinh(0)" to 0.0,
            "acosh(1)" to 0.0,
            "atanh(0)" to 0.0,
            "sqrt(81)" to 9.0,
            "cbrt(-27)" to -3.0,
            "abs(-7)" to 7.0,
            "ln(e)" to 1.0,
            "log(100)" to 2.0,
            "log10(1000)" to 3.0,
            "log2(1024)" to 10.0,
            "exp(0)" to 1.0,
            "pow(2,8)" to 256.0,
            "floor(-1.2)" to -2.0,
            "ceil(-1.2)" to -1.0,
            "round(1.23456,3)" to 1.235,
            "min(2,5,-3)" to -3.0,
            "max(2,5,-3)" to 5.0,
            "mean(2,4,9)" to 5.0,
            "avg(2,4,9)" to 5.0,
            "sum(2,4,9)" to 15.0,
            "fact(5)" to 120.0,
            "factorial(4)" to 24.0,
            "npr(5,2)" to 20.0,
            "ncr(5,2)" to 10.0,
            "sign(-9)" to -1.0,
            "5!" to 120.0,
            "3e-8+2e-8" to 5e-8,
            "1e150/1e149" to 10.0,
        )
        for ((expr, expected) in cases) {
            assertApprox(expected, MathAstEngine.evaluate(expr), 1e-9, expr)
        }

        // Angle units
        assertApprox(0.5, MathAstEngine.evaluate("sin(30)", MathEvalContext(angleMode = AngleMode.DEG)), 1e-9, "deg")
        assertApprox(1.0, MathAstEngine.evaluate("sin(100)", MathEvalContext(angleMode = AngleMode.GRAD)), 1e-9, "grad")

        // User variables and custom functions
        val ctx = MathEvalContext(
            variables = mapOf("a" to 2.0),
            userFunctions = mapOf("f" to UserDefinedFunction("x", "x^2+1")),
        )
        assertApprox(12.0, MathAstEngine.evaluate("f(3)+a", ctx))

        // Invalid syntax and domain checks
        assertFailsWith<MathSyntaxException> { MathAstEngine.evaluate("1+") }
        assertTrue(MathAstEngine.evaluate("sqrt(-1)").isNaN())
        assertFalse(MathAstEngine.evaluate("1/0").isFinite())

        // Fractional factorial, significant figures, engineering notation
        assertApprox(sqrt(PI) / 2.0, MathAstEngine.factorial(0.5), 1e-9, "0.5!")
        assertEquals("1.235", MathAstEngine.formatSignificantFigures(1.23456, 4))
        assertEquals("16.000×10^-6", MathAstEngine.toEngineeringNotation(0.000016, 3))
    }

    @Test
    fun pd007CalculusPoleRejectionAndTangentRootDetection() {
        assertApprox(12.0, MathAstEngine.numericalDerivative("x^3", 2.0), 1e-5, "d/dx x^3 @ 2")
        assertApprox(9.0, MathAstEngine.numericalIntegral("x^2", 0.0, 3.0), 1e-8, "int x^2 0..3")
        assertFalse(MathAstEngine.numericalIntegral("1/x", 0.0, 3.0).isFinite())

        assertEquals(listOf(-1.0, 1.0), MathAstEngine.findNumericalRoots("x^2-1", -2.0, 2.0))
        // PD-007: pole must NOT be classified as root
        assertEquals(emptyList(), MathAstEngine.findNumericalRoots("1/(x-0.123)", -1.0, 1.0))
        // PD-007: even-multiplicity tangent root MUST be detected
        val tangentRoots = MathAstEngine.findNumericalRoots("(x-0.123)^2", -1.0, 1.0)
        assertTrue(tangentRoots.isNotEmpty(), "Expected tangent root near 0.123")
        assertTrue(abs(tangentRoots.first() - 0.123) < 1e-3, "Got ${tangentRoots.first()}")

        // Complex arithmetic & matrix determinant
        val a = ComplexNumber(3.0, 4.0)
        val b = ComplexNumber(1.0, -2.0)
        assertEquals(ComplexNumber(4.0, 2.0), a + b)
        assertEquals(ComplexNumber(2.0, 6.0), a - b)
        assertEquals(ComplexNumber(11.0, -2.0), a * b)
        assertEquals(ComplexNumber(-1.0, 2.0), a / b)
        assertApprox(-2.0, MathAstEngine.matrixDeterminant(listOf(listOf(2.0, 3.0), listOf(4.0, 5.0))))
        assertApprox(22.0, MathAstEngine.matrixDeterminant(listOf(listOf(1.0, 2.0, 3.0), listOf(0.0, 4.0, 5.0), listOf(1.0, 0.0, 6.0))))
    }

    @Test
    fun all23UnitCategoriesMatchReferenceAndInvertCleanly() {
        assertEquals(23, UnitConverter23.categories.size)
        val checks = listOf(
            Triple("length", "in" to "cm", 12.0 to 30.48),
            Triple("mass", "lb" to "kg", 1.0 to 0.45359237),
            Triple("volume", "gal" to "L", 1.0 to 3.785411784),
            Triple("temperature", "C" to "F", -40.0 to -40.0),
            Triple("area", "ha" to "m2", 3.0 to 30000.0),
            Triple("time", "hr" to "s", 2.0 to 7200.0),
            Triple("speed", "kph" to "mps", 36.0 to 10.0),
            Triple("acceleration", "g0" to "mps2", 1.0 to 9.80665),
            Triple("pressure", "atm" to "Pa", 1.0 to 101325.0),
            Triple("energy", "kWh" to "J", 1.0 to 3600000.0),
            Triple("power", "kW" to "W", 2.0 to 2000.0),
            Triple("force", "kN" to "N", 2.0 to 2000.0),
            Triple("electrical", "A" to "mA", 1.0 to 1000.0),
            Triple("frequency", "rpm" to "Hz", 60.0 to 1.0),
            Triple("data", "bit" to "B", 8.0 to 1.0),
            Triple("dataRate", "MBps" to "Mbps", 1.0 to 8.0),
            Triple("density", "g_cm3" to "kg_m3", 2.0 to 2000.0),
            Triple("concentration", "g_L" to "mg_mL", 4.0 to 4.0),
            Triple("flow", "L_s" to "L_min", 1.0 to 60.0),
            Triple("angle", "turn" to "deg", 0.25 to 90.0),
            Triple("torque", "inlb" to "Nm", 12.0 to 1.35582),
            Triple("fuel", "mpg" to "l100km", 23.521458 to 10.0),
            Triple("radiation", "rem" to "mSv", 1.0 to 10.0),
        )
        for ((cat, pair, vals) in checks) {
            val out = UnitConverter23.convert(cat, pair.first, pair.second, vals.first)
            assertApprox(vals.second, out, 2e-5, "$cat ${pair.first}->${pair.second}")
            val roundTrip = UnitConverter23.convert(cat, pair.second, pair.first, out)
            assertApprox(vals.first, roundTrip, 1e-7, "$cat roundTrip")
        }
        // Invertibility across every declared unit
        for (cat in UnitConverter23.categories.values) {
            for (u in cat.units) {
                for (x in listOf(0.125, 7.0, 100.0)) {
                    assertApprox(x, u.fromBase(u.toBase(x)), 1e-7, "${cat.id}.${u.id}($x)")
                }
            }
        }
    }

    @Test
    fun labAnalyticalCalculationsAndWetVsNirReferenceVectors() {
        assertApprox(10.0, LabAnalyticsEngine.dilutionVolumeV1(200.0, 20.0, 100.0))
        assertApprox(90.0, LabAnalyticsEngine.percentRecovery(11.0, 2.0, 10.0))
        val rsd = LabAnalyticsEngine.computeRsd(listOf(9.0, 10.0, 11.0))
        assertEquals(3, rsd.n)
        assertApprox(10.0, rsd.mean)
        assertApprox(1.0, rsd.sampleSd!!)
        assertApprox(10.0, rsd.rsdPct!!)

        fun pair(i: Int, wet: Double, nir: Double, excluded: Boolean = false, unit: String = "%", wetRep: List<Double> = emptyList(), nirRep: List<Double> = emptyList()) =
            LabWetNirPair("p$i", "ws_lab", "S$i", "Grain", "Moisture", "AOAC", wet, wetRep, nir, nirRep, unit, "NIR-1", "M1", "2026-11-25", "Analyst", excluded)

        val summary1 = LabAnalyticsEngine.compareWetVsNir(listOf(pair(1, 1.0, 2.0), pair(2, 2.0, 4.0), pair(3, 3.0, 6.0), pair(4, 9.0, 8.0, excluded = true)))
        assertEquals(3, summary1.nIncluded)
        assertEquals(1, summary1.nExcluded)
        assertApprox(2.0, summary1.meanWet!!)
        assertApprox(4.0, summary1.meanNir!!)
        assertApprox(2.0, summary1.bias!!)
        assertApprox(1.0, summary1.sdDiffSep!!)
        assertApprox(sqrt(14.0 / 3.0), summary1.rmse!!)
        assertApprox(2.0, summary1.slope!!)
        assertApprox(0.0, summary1.intercept!!)
        assertApprox(1.0, summary1.pearsonR!!)
        assertApprox(1.0, summary1.rSquared!!)

        val summary2 = LabAnalyticsEngine.compareWetVsNir(
            listOf(
                pair(1, 2.0, 4.0, wetRep = listOf(0.0, 0.0), nirRep = listOf(2.0, 4.0)),
                pair(2, 2.0, 4.0, unit = "mg/L"),
                pair(3, Double.NaN, 4.0),
            )
        )
        assertTrue(summary2.mixedUnitsWarning)
        assertEquals(2, summary2.nIncluded)
        assertApprox(0.0, summary2.includedPoints[0].wetMean)
        assertApprox(3.0, summary2.includedPoints[0].nirMean)
        assertNull(summary2.includedPoints[0].pctDiff)
    }

    @Test
    fun spreadsheetFormulaEngineReferenceSuiteIncludingCont006AndCont007() {
        fun makeSheets(expr: String): List<WorksheetModel> = listOf(
            WorksheetModel(
                id = "main",
                name = "Main",
                namedRanges = mapOf("Values" to "A1:A3"),
                cells = mapOf(
                    "A1" to SheetCellInput("2"),
                    "A2" to SheetCellInput("4"),
                    "A3" to SheetCellInput("6"),
                    "B1" to SheetCellInput("corn"),
                    "B2" to SheetCellInput("soy"),
                    "B3" to SheetCellInput("corn"),
                    "E1" to SheetCellInput(expr),
                ),
            ),
            WorksheetModel(id = "other", name = "Other", cells = mapOf("A1" to SheetCellInput("9"))),
        )

        val numCases = listOf(
            "=SUM(A1:A3)" to 12.0,
            "=AVERAGE(A1:A3)" to 4.0,
            "=AVG(A1:A3)" to 4.0,
            "=COUNT(A1:B3)" to 3.0,
            "=COUNTA(A1:B3)" to 6.0,
            "=MIN(A1:A3)" to 2.0,
            "=MAX(A1:A3)" to 6.0,
            "=MEDIAN(A1:A3)" to 4.0,
            "=STDEV(A1:A3)" to 2.0,
            "=STDEV_S(A1:A3)" to 2.0,
            "=COUNTIF(A1:A3,\">3\")" to 2.0,
            "=SUMIF(B1:B3,\"corn\",A1:A3)" to 8.0,
            "=XLOOKUP(\"soy\",B1:B3,A1:A3,\"missing\")" to 4.0,
            "=LEN(\"abc\")" to 3.0,
            "=PMT(0,10,100)" to -10.0,
            "=NPV(0.1,110,121)" to 200.0,
            "=SUM(Values)" to 12.0,
            "=Other!A1+A1" to 11.0,
            "=SUM(A1:A3)+1" to 13.0, // CONT-007 composite formula
        )
        for ((formula, expected) in numCases) {
            val evaluated = SpreadsheetFormulaEngine.evaluateWorkbook(makeSheets(formula), "2026-11-25")
            val cell = evaluated["main"]!!["E1"]!!.value
            assertTrue(cell is SheetValue.NumberVal, "$formula produced $cell")
            assertApprox(expected, cell.number, 1e-8, formula)
        }

        val strCases = listOf(
            "=IF(A1<A2,\"yes\",\"no\")" to "yes",
            "=AND(A1>0,A2<5)" to "TRUE",
            "=OR(A1>3,A2>3)" to "TRUE",
            "=XLOOKUP(\"rice\",B1:B3,A1:A3,\"missing\")" to "missing",
            "=CONCAT(\"A\",A1)" to "A2",
            "=CONCATENATE(\"A\",\"B\")" to "AB",
            "=UPPER(\"Hi\")" to "HI",
            "=LOWER(\"Hi\")" to "hi",
            "=LEFT(\"abc\",2)" to "ab",
            "=RIGHT(\"abc\",2)" to "bc",
            "=TODAY()" to "2026-11-25",
            "=LEFT(\"abc\",0)" to "", // CONT-007 zero-length LEFT
            "=RIGHT(\"abc\",0)" to "", // CONT-007 zero-length RIGHT
            "=Missing!A1+1" to "#REF!", // CONT-006 missing sheet error
        )
        for ((formula, expected) in strCases) {
            val evaluated = SpreadsheetFormulaEngine.evaluateWorkbook(makeSheets(formula), "2026-11-25")
            assertEquals(expected, evaluated["main"]!!["E1"]!!.value.displayString(), formula)
        }

        // Division by zero & circular reference
        val divZero = SpreadsheetFormulaEngine.evaluateWorkbook(makeSheets("=1/0"))["main"]!!["E1"]!!
        assertEquals("#DIV/0!", divZero.error)

        val circSheet = listOf(
            WorksheetModel(
                id = "main", name = "Main",
                cells = mapOf("A1" to SheetCellInput("=E1"), "E1" to SheetCellInput("=A1")),
            )
        )
        assertEquals("#CIRC!", SpreadsheetFormulaEngine.evaluateWorkbook(circSheet)["main"]!!["E1"]!!.error)
    }
}
