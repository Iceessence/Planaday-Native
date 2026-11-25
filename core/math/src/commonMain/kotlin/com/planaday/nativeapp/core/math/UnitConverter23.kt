package com.planaday.nativeapp.core.math

data class PhysicalUnit(
    val id: String,
    val symbol: String,
    val label: String,
    val toBase: (Double) -> Double,
    val fromBase: (Double) -> Double,
)

data class UnitCategoryDef(
    val id: String,
    val displayName: String,
    val baseUnitId: String,
    val units: List<PhysicalUnit>,
)

/**
 * 23-category invertible physical unit converter (R-0277, UN-001..UN-024).
 * Every declared unit transform is strictly invertible over its physical domain.
 */
object UnitConverter23 {
    private fun linear(id: String, symbol: String, label: String, factorToBase: Double) = PhysicalUnit(
        id = id,
        symbol = symbol,
        label = label,
        toBase = { it * factorToBase },
        fromBase = { it / factorToBase },
    )

    val categories: Map<String, UnitCategoryDef> = listOf(
        UnitCategoryDef(
            id = "length", displayName = "Length", baseUnitId = "m",
            units = listOf(
                linear("m", "m", "Meter", 1.0),
                linear("km", "km", "Kilometer", 1000.0),
                linear("cm", "cm", "Centimeter", 0.01),
                linear("mm", "mm", "Millimeter", 0.001),
                linear("um", "µm", "Micrometer", 1e-6),
                linear("in", "in", "Inch", 0.0254),
                linear("ft", "ft", "Foot", 0.3048),
                linear("yd", "yd", "Yard", 0.9144),
                linear("mi", "mi", "Mile", 1609.344),
            ),
        ),
        UnitCategoryDef(
            id = "mass", displayName = "Mass", baseUnitId = "kg",
            units = listOf(
                linear("kg", "kg", "Kilogram", 1.0),
                linear("g", "g", "Gram", 1e-3),
                linear("mg", "mg", "Milligram", 1e-6),
                linear("ug", "µg", "Microgram", 1e-9),
                linear("lb", "lb", "Pound", 0.45359237),
                linear("oz", "oz", "Ounce", 0.028349523125),
                linear("t", "t", "Metric Tonne", 1000.0),
            ),
        ),
        UnitCategoryDef(
            id = "volume", displayName = "Volume", baseUnitId = "L",
            units = listOf(
                linear("L", "L", "Liter", 1.0),
                linear("mL", "mL", "Milliliter", 1e-3),
                linear("uL", "µL", "Microliter", 1e-6),
                linear("m3", "m³", "Cubic Meter", 1000.0),
                linear("gal", "gal", "US Gallon", 3.785411784),
                linear("qt", "qt", "US Quart", 0.946352946),
                linear("cup", "cup", "US Cup", 0.2365882365),
                linear("fl_oz", "fl oz", "US Fluid Ounce", 0.0295735295625),
                linear("tbsp", "tbsp", "Tablespoon", 0.01478676478125),
                linear("tsp", "tsp", "Teaspoon", 0.00492892159375),
            ),
        ),
        UnitCategoryDef(
            id = "temperature", displayName = "Temperature", baseUnitId = "K",
            units = listOf(
                PhysicalUnit("K", "K", "Kelvin", { it }, { it }),
                PhysicalUnit("C", "°C", "Celsius", { it + 273.15 }, { it - 273.15 }),
                PhysicalUnit("F", "°F", "Fahrenheit", { (it - 32.0) * 5.0 / 9.0 + 273.15 }, { (it - 273.15) * 9.0 / 5.0 + 32.0 }),
            ),
        ),
        UnitCategoryDef(
            id = "area", displayName = "Area", baseUnitId = "m2",
            units = listOf(
                linear("m2", "m²", "Square Meter", 1.0),
                linear("km2", "km²", "Square Kilometer", 1e6),
                linear("cm2", "cm²", "Square Centimeter", 1e-4),
                linear("ha", "ha", "Hectare", 10_000.0),
                linear("acre", "ac", "Acre", 4046.8564224),
                linear("ft2", "ft²", "Square Foot", 0.09290304),
            ),
        ),
        UnitCategoryDef(
            id = "time", displayName = "Time", baseUnitId = "s",
            units = listOf(
                linear("s", "s", "Second", 1.0),
                linear("ms", "ms", "Millisecond", 1e-3),
                linear("min", "min", "Minute", 60.0),
                linear("hr", "hr", "Hour", 3600.0),
                linear("day", "d", "Day", 86400.0),
                linear("wk", "wk", "Week", 604800.0),
            ),
        ),
        UnitCategoryDef(
            id = "speed", displayName = "Speed", baseUnitId = "mps",
            units = listOf(
                linear("mps", "m/s", "Meters/sec", 1.0),
                linear("kph", "km/h", "Kilometers/hr", 1.0 / 3.6),
                linear("mph", "mph", "Miles/hr", 0.44704),
                linear("kn", "kn", "Knot", 1852.0 / 3600.0),
            ),
        ),
        UnitCategoryDef(
            id = "acceleration", displayName = "Acceleration", baseUnitId = "mps2",
            units = listOf(
                linear("mps2", "m/s²", "Meters/sec²", 1.0),
                linear("g0", "g₀", "Standard Gravity", 9.80665),
                linear("ftps2", "ft/s²", "Feet/sec²", 0.3048),
            ),
        ),
        UnitCategoryDef(
            id = "pressure", displayName = "Pressure", baseUnitId = "Pa",
            units = listOf(
                linear("Pa", "Pa", "Pascal", 1.0),
                linear("kPa", "kPa", "Kilopascal", 1000.0),
                linear("bar", "bar", "Bar", 100_000.0),
                linear("atm", "atm", "Atmosphere", 101_325.0),
                linear("psi", "psi", "Pounds/in²", 6894.757293168),
                linear("mmHg", "mmHg", "Millimeter of Mercury", 133.322387415),
            ),
        ),
        UnitCategoryDef(
            id = "energy", displayName = "Energy", baseUnitId = "J",
            units = listOf(
                linear("J", "J", "Joule", 1.0),
                linear("kJ", "kJ", "Kilojoule", 1000.0),
                linear("cal", "cal", "Calorie", 4.184),
                linear("kcal", "kcal", "Kilocalorie", 4184.0),
                linear("Wh", "Wh", "Watt-hour", 3600.0),
                linear("kWh", "kWh", "Kilowatt-hour", 3_600_000.0),
                linear("BTU", "BTU", "British Thermal Unit", 1055.05585262),
            ),
        ),
        UnitCategoryDef(
            id = "power", displayName = "Power", baseUnitId = "W",
            units = listOf(
                linear("W", "W", "Watt", 1.0),
                linear("kW", "kW", "Kilowatt", 1000.0),
                linear("MW", "MW", "Megawatt", 1e6),
                linear("hp", "hp", "Mechanical Horsepower", 745.6998715822702),
            ),
        ),
        UnitCategoryDef(
            id = "force", displayName = "Force", baseUnitId = "N",
            units = listOf(
                linear("N", "N", "Newton", 1.0),
                linear("kN", "kN", "Kilonewton", 1000.0),
                linear("lbf", "lbf", "Pound-force", 4.4482216152605),
                linear("dyn", "dyn", "Dyne", 1e-5),
            ),
        ),
        UnitCategoryDef(
            id = "electrical", displayName = "Electrical Current", baseUnitId = "A",
            units = listOf(
                linear("A", "A", "Ampere", 1.0),
                linear("mA", "mA", "Milliampere", 1e-3),
                linear("uA", "µA", "Microampere", 1e-6),
                linear("kA", "kA", "Kiloampere", 1000.0),
            ),
        ),
        UnitCategoryDef(
            id = "frequency", displayName = "Frequency", baseUnitId = "Hz",
            units = listOf(
                linear("Hz", "Hz", "Hertz", 1.0),
                linear("kHz", "kHz", "Kilohertz", 1e3),
                linear("MHz", "MHz", "Megahertz", 1e6),
                linear("GHz", "GHz", "Gigahertz", 1e9),
                linear("rpm", "rpm", "Revolutions/min", 1.0 / 60.0),
            ),
        ),
        UnitCategoryDef(
            id = "data", displayName = "Data Storage", baseUnitId = "B",
            units = listOf(
                linear("B", "B", "Byte", 1.0),
                linear("bit", "b", "Bit", 0.125),
                linear("KB", "KB", "Kilobyte (1000)", 1e3),
                linear("MB", "MB", "Megabyte (1000²)", 1e6),
                linear("GB", "GB", "Gigabyte (1000³)", 1e9),
                linear("KiB", "KiB", "Kibibyte (1024)", 1024.0),
                linear("MiB", "MiB", "Mebibyte (1024²)", 1_048_576.0),
            ),
        ),
        UnitCategoryDef(
            id = "dataRate", displayName = "Data Rate", baseUnitId = "bps",
            units = listOf(
                linear("bps", "bps", "Bits/sec", 1.0),
                linear("kbps", "kbps", "Kilobits/sec", 1e3),
                linear("Mbps", "Mbps", "Megabits/sec", 1e6),
                linear("Gbps", "Gbps", "Gigabits/sec", 1e9),
                linear("KBps", "KB/s", "Kilobytes/sec", 8e3),
                linear("MBps", "MB/s", "Megabytes/sec", 8e6),
            ),
        ),
        UnitCategoryDef(
            id = "density", displayName = "Density", baseUnitId = "kg_m3",
            units = listOf(
                linear("kg_m3", "kg/m³", "Kilogram/m³", 1.0),
                linear("g_cm3", "g/cm³", "Gram/cm³", 1000.0),
                linear("g_L", "g/L", "Gram/L", 1.0),
                linear("lb_ft3", "lb/ft³", "Pound/ft³", 16.01846337396),
            ),
        ),
        UnitCategoryDef(
            id = "concentration", displayName = "Concentration", baseUnitId = "g_L",
            units = listOf(
                linear("g_L", "g/L", "Gram/Liter", 1.0),
                linear("mg_mL", "mg/mL", "Milligram/mL", 1.0),
                linear("mg_L", "mg/L", "Milligram/L (ppm)", 1e-3),
                linear("ug_L", "µg/L", "Microgram/L (ppb)", 1e-6),
                linear("pct_wv", "% w/v", "Percent w/v", 10.0),
            ),
        ),
        UnitCategoryDef(
            id = "flow", displayName = "Volumetric Flow", baseUnitId = "L_s",
            units = listOf(
                linear("L_s", "L/s", "Liters/sec", 1.0),
                linear("L_min", "L/min", "Liters/min", 1.0 / 60.0),
                linear("mL_min", "mL/min", "Milliliters/min", 1e-3 / 60.0),
                linear("gpm", "gpm", "US Gallons/min", 3.785411784 / 60.0),
            ),
        ),
        UnitCategoryDef(
            id = "angle", displayName = "Angle", baseUnitId = "rad",
            units = listOf(
                linear("rad", "rad", "Radian", 1.0),
                linear("deg", "°", "Degree", kotlin.math.PI / 180.0),
                linear("grad", "gon", "Gradian", kotlin.math.PI / 200.0),
                linear("turn", "turn", "Full Turn", 2.0 * kotlin.math.PI),
            ),
        ),
        UnitCategoryDef(
            id = "torque", displayName = "Torque", baseUnitId = "Nm",
            units = listOf(
                linear("Nm", "N·m", "Newton-meter", 1.0),
                linear("ftlb", "ft·lb", "Foot-pound", 1.3558179483314),
                linear("inlb", "in·lb", "Inch-pound", 0.1129848290276167),
            ),
        ),
        UnitCategoryDef(
            id = "fuel", displayName = "Fuel Economy", baseUnitId = "km_L",
            units = listOf(
                PhysicalUnit("km_L", "km/L", "Kilometers/Liter", { it }, { it }),
                PhysicalUnit("mpg", "mpg", "Miles/US Gallon", { it * 0.425143707430272 }, { it / 0.425143707430272 }),
                PhysicalUnit("l100km", "L/100km", "Liters/100km", { 100.0 / it }, { 100.0 / it }),
            ),
        ),
        UnitCategoryDef(
            id = "radiation", displayName = "Radiation Dose Equivalent", baseUnitId = "Sv",
            units = listOf(
                linear("Sv", "Sv", "Sievert", 1.0),
                linear("mSv", "mSv", "Millisievert", 1e-3),
                linear("uSv", "µSv", "Microsievert", 1e-6),
                linear("rem", "rem", "Rem", 0.01),
                linear("mrem", "mrem", "Millirem", 1e-5),
            ),
        ),
    ).associateBy { it.id }

    fun convert(categoryId: String, fromUnitId: String, toUnitId: String, value: Double): Double {
        val cat = categories[categoryId] ?: throw IllegalArgumentException("Unknown unit category '$categoryId'")
        val from = cat.units.firstOrNull { it.id == fromUnitId }
            ?: throw IllegalArgumentException("Unknown source unit '$fromUnitId' in '$categoryId'")
        val to = cat.units.firstOrNull { it.id == toUnitId }
            ?: throw IllegalArgumentException("Unknown target unit '$toUnitId' in '$categoryId'")
        return to.fromBase(from.toBase(value))
    }
}
