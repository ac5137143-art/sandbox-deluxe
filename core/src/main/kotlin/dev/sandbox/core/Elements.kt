package dev.sandbox.core

/** Ambient world temperature in \u00b0C. */
const val AMBIENT = 22f

/**
 * Aggregate state of matter. Drives which movement rules apply to a cell.
 */
enum class Phase { EMPTY, POWDER, LIQUID, GAS, SOLID, ENERGY, LIFE, SPECIAL }

/** Palette grouping, shown as tabs in the element picker. */
enum class Category(val title: String) {
    TOOLS("Инструменты"),
    POWDERS("Сыпучие"),
    LIQUIDS("Жидкости"),
    GASES("Газы"),
    SOLIDS("Твёрдые"),
    ENERGY("Энергия"),
    LIFE("Жизнь"),
    SPECIAL("Механизмы"),
}

/**
 * A material. Instances are created through [El] and are immutable after [El.ALL] is resolved:
 * the `*Into` fields hold resolved element ids for phase transitions.
 */
class Element internal constructor(
    val id: Int,
    val key: String,
    val name: String,
    val category: Category,
    val phase: Phase,
    val color: Int,
) {
    /** Relative density; air is 10. Decides what displaces what. */
    var density: Int = 100

    /** Per-cell colour jitter amplitude (0..32) for a less flat look. */
    var jitter: Int = 6

    /** How far a liquid may slide sideways per tick (cells). */
    var dispersion: Int = 0

    /** Percentage of ticks in which a powder may slide down a slope (angle of repose). */
    var slide: Int = 100

    /** 0..100; higher means more ticks are skipped while flowing. */
    var viscosity: Int = 0

    /** Heat conduction coefficient, 0..1. */
    var conductivity: Float = 0.06f

    /** Relative volumetric heat capacity (water = 1). */
    var heatCapacity: Float = 1f

    /** Explosion / acid resistance, 0..20. */
    var strength: Int = 2

    /** 0 = dissolves instantly in acid, 1 = immune. */
    var acidResist: Float = 0.35f

    var indestructible: Boolean = false

    /** Self-illumination 0..255, used by the renderer. */
    var glow: Int = 0

    /** Opacity 0..255 of the rendered cell. */
    var alpha: Int = 255

    /** True for light gases/fire that float upwards. */
    var rises: Boolean = false

    /** Suspended powders (ash, dust) sink slower through liquids. */
    var suspended: Boolean = false

    /** Conducts electricity. */
    var conductive: Boolean = false

    /** Explosion radius in cells when the material detonates. */
    var explosive: Float = 0f

    /** Flames need this much oxygen-rich neighbourhood? (0 = no, 1 = yes) */
    var needsAir: Boolean = false

    // ---- temperature driven transitions -------------------------------------
    var meltPoint: Float = Float.NaN
    var meltKey: String? = null
    var boilPoint: Float = Float.NaN
    var boilKey: String? = null
    var freezePoint: Float = Float.NaN
    var freezeKey: String? = null
    var ignition: Float = Float.NaN
    var burnKey: String? = null
    var burnTicks: Int = 0
    var flameTemp: Float = 0f
    var decayTicks: Int = 0
    var decayKey: String? = null
    var heatSource: Float = Float.NaN
    var spawnTemp: Float = Float.NaN

    // ---- resolved ids (filled by El.ALL) ------------------------------------
    internal var meltInto: Int = 0
    internal var boilInto: Int = 0
    internal var freezeInto: Int = 0
    internal var burnInto: Int = 0
    internal var decayInto: Int = 0

    val flammable: Boolean get() = !ignition.isNaN()
    val melts: Boolean get() = !meltPoint.isNaN()
    val boils: Boolean get() = !boilPoint.isNaN()
    val condenses: Boolean get() = !freezePoint.isNaN()
    val decays: Boolean get() = decayTicks > 0

    // ---- authoring DSL ------------------------------------------------------
    fun meltsAt(temp: Float, into: String) { meltPoint = temp; meltKey = into }

    fun boilsAt(temp: Float, into: String) { boilPoint = temp; boilKey = into }

    fun condensesAt(temp: Float, into: String) { freezePoint = temp; freezeKey = into }

    fun freezesAt(temp: Float, into: String) { freezePoint = temp; freezeKey = into }

    fun burnsAt(temp: Float, into: String?, ticks: Int, flame: Float) {
        ignition = temp; burnKey = into; burnTicks = ticks; flameTemp = flame
    }

    fun decaysInto(ticks: Int, into: String?) { decayTicks = ticks; decayKey = into }

    fun emitsHeat(temp: Float) { heatSource = temp }

    fun spawnsAt(temp: Float) { spawnTemp = temp }

    fun detonates(power: Float) { explosive = power }

    /** Human readable summary for the UI info panel. */
    fun info(): String {
        val sb = StringBuilder()
        sb.append(phaseName)
        if (!spawnTemp.isNaN()) sb.append(" · ").append("старт ${spawnTemp.toInt()}\u00b0C")
        if (melts) sb.append(" · плавится ${meltPoint.toInt()}\u00b0C")
        if (boils) sb.append(" · кипит ${boilPoint.toInt()}\u00b0C")
        if (condenses) sb.append(" · переход ${freezePoint.toInt()}\u00b0C")
        if (flammable) sb.append(" · вспыхивает ${ignition.toInt()}\u00b0C")
        if (explosive > 0f) sb.append(" · взрывчатка")
        if (conductive) sb.append(" · проводит ток")
        if (indestructible) sb.append(" · неразрушима")
        return sb.toString()
    }

    private val phaseName: String
        get() = when (phase) {
            Phase.EMPTY -> "Пустота"
            Phase.POWDER -> "Сыпучее"
            Phase.LIQUID -> "Жидкость"
            Phase.GAS -> "Газ"
            Phase.SOLID -> "Твёрдое"
            Phase.ENERGY -> "Энергия"
            Phase.LIFE -> "Живое"
            Phase.SPECIAL -> "Механизм"
        }
}

/**
 * The material registry. Element ids are their index in [ALL]; any transition target is declared
 * by key so that definitions may reference materials declared later.
 */
object El {
    private val defs = ArrayList<Element>()

    private fun def(
        key: String,
        name: String,
        category: Category,
        phase: Phase,
        color: Int,
        block: Element.() -> Unit = {},
    ): Element {
        val e = Element(defs.size, key, name, category, phase, color)
        e.block()
        defs += e
        return e
    }

    // ---------------------------------------------------------------- tools --
    val EMPTY = def("empty", "Ластик", Category.TOOLS, Phase.EMPTY, 0x00000000.toInt())

    val WALL = def("wall", "Стена", Category.TOOLS, Phase.SOLID, 0xFF3E4A5C.toInt()) {
        indestructible = true
        strength = 20
        conductivity = 0.05f
        acidResist = 1f
    }

    // -------------------------------------------------------------- powders --
    val SAND = def("sand", "Песок", Category.POWDERS, Phase.POWDER, 0xFFE3C173.toInt()) {
        density = 160; heatCapacity = 0.9f; conductivity = 0.08f; strength = 1
        meltsAt(1700f, "molten_glass")
    }

    val GRAVEL = def("gravel", "Гравий", Category.POWDERS, Phase.POWDER, 0xFF8C8C90.toInt()) {
        density = 195; heatCapacity = 0.85f; conductivity = 0.09f; strength = 3; slide = 22
        meltsAt(1500f, "lava")
    }

    val DIRT = def("dirt", "Земля", Category.POWDERS, Phase.POWDER, 0xFF6B4A2B.toInt()) {
        density = 150; heatCapacity = 0.9f; conductivity = 0.06f; strength = 2; slide = 55
    }

    val ASH = def("ash", "Пепел", Category.POWDERS, Phase.POWDER, 0xFF75757A.toInt()) {
        density = 55; jitter = 12; conductivity = 0.04f; strength = 1; suspended = true
    }

    val COAL = def("coal", "Уголь", Category.POWDERS, Phase.POWDER, 0xFF2A2A30.toInt()) {
        density = 125; conductivity = 0.05f; strength = 2
        burnsAt(420f, "ash", 900, 950f)
    }

    val GUNPOWDER = def("gunpowder", "Порох", Category.POWDERS, Phase.POWDER, 0xFF3A3A44.toInt()) {
        density = 130; conductivity = 0.05f; strength = 1
        burnsAt(160f, null, 12, 1200f)
        detonates(4.5f)
    }

    val THERMITE = def("thermite", "Термит", Category.POWDERS, Phase.POWDER, 0xFF8A5A3A.toInt()) {
        density = 210; conductivity = 0.12f; strength = 3; slide = 80
        burnsAt(900f, "molten_metal", 260, 2400f)
    }

    val SALT = def("salt", "Соль", Category.POWDERS, Phase.POWDER, 0xFFF0F0F5.toInt()) {
        density = 140; conductivity = 0.07f; strength = 1
        boilsAt(1465f, "lava")
    }

    val SNOW = def("snow", "Снег", Category.POWDERS, Phase.POWDER, 0xFFEFF7FF.toInt()) {
        density = 40; jitter = 10; conductivity = 0.12f; strength = 1; suspended = true; slide = 85
        spawnsAt(-14f)
        meltsAt(0.5f, "water")
    }

    // -------------------------------------------------------------- liquids --
    val WATER = def("water", "Вода", Category.LIQUIDS, Phase.LIQUID, 0xFF3A72DC.toInt()) {
        density = 100; dispersion = 6; conductivity = 0.24f; heatCapacity = 1f
        alpha = 235; acidResist = 0.8f
        freezesAt(-1f, "ice")
        boilsAt(100f, "steam")
    }

    val SALT_WATER = def("salt_water", "Солёная вода", Category.LIQUIDS, Phase.LIQUID, 0xFF2A5FB4.toInt()) {
        density = 108; dispersion = 6; conductivity = 0.26f; heatCapacity = 1.05f
        alpha = 240; acidResist = 0.8f
        freezesAt(-8f, "ice")
        boilsAt(103f, "steam")
    }

    val OIL = def("oil", "Нефть", Category.LIQUIDS, Phase.LIQUID, 0xFF4A3A14.toInt()) {
        density = 80; dispersion = 3; viscosity = 12; conductivity = 0.14f; heatCapacity = 0.55f
        alpha = 245
        burnsAt(300f, null, 420, 900f)
    }

    val PETROL = def("petrol", "Бензин", Category.LIQUIDS, Phase.LIQUID, 0xFF9A7A2A.toInt()) {
        density = 72; dispersion = 6; conductivity = 0.12f; heatCapacity = 0.5f
        alpha = 220
        boilsAt(95f, "smoke")
        burnsAt(35f, null, 320, 1100f)
    }

    val ALCOHOL = def("alcohol", "Спирт", Category.LIQUIDS, Phase.LIQUID, 0xFFB9CBE8.toInt()) {
        density = 79; dispersion = 6; conductivity = 0.18f; heatCapacity = 0.6f
        alpha = 220
        boilsAt(78f, "smoke")
        burnsAt(60f, null, 260, 800f)
    }

    val ACID = def("acid", "Кислота", Category.LIQUIDS, Phase.LIQUID, 0xFF7ED43A.toInt()) {
        density = 112; dispersion = 5; conductivity = 0.2f; heatCapacity = 0.9f
        alpha = 235; acidResist = 1f
        boilsAt(110f, "steam")
    }

    val LAVA = def("lava", "Лава", Category.LIQUIDS, Phase.LIQUID, 0xFFE8501E.toInt()) {
        density = 250; dispersion = 1; viscosity = 35; conductivity = 0.35f; heatCapacity = 1.3f
        strength = 20; glow = 200
        spawnsAt(1150f)
        freezesAt(700f, "stone")
        boilsAt(2600f, "plasma")
    }

    val MOLTEN_METAL = def("molten_metal", "Расплав металла", Category.LIQUIDS, Phase.LIQUID, 0xFFFFA03A.toInt()) {
        density = 700; dispersion = 4; conductivity = 0.5f; heatCapacity = 0.7f
        glow = 190; spawnsAt(1550f)
        freezesAt(1420f, "metal")
        boilsAt(2900f, "plasma")
    }

    val MOLTEN_GLASS = def("molten_glass", "Расплав стекла", Category.LIQUIDS, Phase.LIQUID, 0xFFFFE08A.toInt()) {
        density = 240; dispersion = 2; viscosity = 25; conductivity = 0.3f; heatCapacity = 0.9f
        glow = 170; spawnsAt(1500f)
        freezesAt(1050f, "glass")
    }

    val MERCURY = def("mercury", "Ртуть", Category.LIQUIDS, Phase.LIQUID, 0xFFB4B8C4.toInt()) {
        density = 1350; dispersion = 4; conductivity = 0.6f; heatCapacity = 0.3f
        strength = 4; conductive = true
        boilsAt(357f, "smoke")
    }

    val LIQUID_NITROGEN = def("liquid_nitrogen", "Жидкий азот", Category.LIQUIDS, Phase.LIQUID, 0xFFA8E8FF.toInt()) {
        density = 81; dispersion = 6; conductivity = 0.3f; heatCapacity = 0.45f
        alpha = 230; spawnsAt(-196f)
        boilsAt(-180f, "nitrogen")
    }

    val SLIME = def("slime", "Слизь", Category.LIQUIDS, Phase.LIQUID, 0xFF4FA83C.toInt()) {
        density = 130; dispersion = 1; viscosity = 85; conductivity = 0.15f
        alpha = 240; burnsAt(250f, null, 200, 600f)
    }

    // ----------------------------------------------------------------- gases --
    val STEAM = def("steam", "Пар", Category.GASES, Phase.GAS, 0xFFC6D6E6.toInt()) {
        density = 5; rises = true; conductivity = 0.1f; heatCapacity = 0.35f; dispersion = 2
        alpha = 110; spawnsAt(135f)
        condensesAt(96f, "water")
        decaysInto(2400, "water")
    }

    val SMOKE = def("smoke", "Дым", Category.GASES, Phase.GAS, 0xFF3C3C42.toInt()) {
        density = 7; rises = true; conductivity = 0.08f; heatCapacity = 0.3f; dispersion = 2
        alpha = 135; spawnsAt(120f)
        decaysInto(420, null)
    }

    val HYDROGEN = def("hydrogen", "Водород", Category.GASES, Phase.GAS, 0xFFD8F0FF.toInt()) {
        density = 2; rises = true; conductivity = 0.35f; heatCapacity = 0.25f; dispersion = 3
        alpha = 70
        burnsAt(400f, "fire", 30, 1600f)
        detonates(6f)
    }

    val OXYGEN = def("oxygen", "Кислород", Category.GASES, Phase.GAS, 0xFF9FD4FF.toInt()) {
        density = 11; conductivity = 0.12f; heatCapacity = 0.25f; dispersion = 2
        alpha = 60
    }

    val METHANE = def("methane", "Метан", Category.GASES, Phase.GAS, 0xFFB7D8A6.toInt()) {
        density = 7; rises = true; conductivity = 0.1f; heatCapacity = 0.3f; dispersion = 3
        alpha = 70
        burnsAt(540f, "fire", 30, 1500f)
        detonates(7f)
    }

    val CO2 = def("co2", "CO2", Category.GASES, Phase.GAS, 0xFF8A9AA8.toInt()) {
        density = 15; conductivity = 0.1f; heatCapacity = 0.35f; dispersion = 2
        alpha = 70
        decaysInto(3000, null)
    }

    val CHLORINE = def("chlorine", "Хлор", Category.GASES, Phase.GAS, 0xFFC8E060.toInt()) {
        density = 25; conductivity = 0.1f; heatCapacity = 0.3f; dispersion = 2
        alpha = 90
        decaysInto(2000, null)
    }

    val NITROGEN = def("nitrogen", "Азот", Category.GASES, Phase.GAS, 0xFFC8D8E8.toInt()) {
        density = 10; conductivity = 0.1f; heatCapacity = 0.3f; dispersion = 2
        alpha = 55; spawnsAt(-180f)
        decaysInto(3000, null)
    }

    // ---------------------------------------------------------------- solids --
    val STONE = def("stone", "Камень", Category.SOLIDS, Phase.SOLID, 0xFF6A6A72.toInt()) {
        strength = 8; conductivity = 0.2f; heatCapacity = 0.9f
        meltsAt(1200f, "lava")
    }

    val WOOD = def("wood", "Дерево", Category.SOLIDS, Phase.SOLID, 0xFF8A5A2B.toInt()) {
        strength = 4; conductivity = 0.07f; heatCapacity = 0.6f
        burnsAt(300f, "ash", 700, 850f)
    }

    val METAL = def("metal", "Металл", Category.SOLIDS, Phase.SOLID, 0xFF9AA2AE.toInt()) {
        strength = 14; conductivity = 0.62f; heatCapacity = 0.45f; conductive = true
        meltsAt(1500f, "molten_metal")
    }

    val COPPER = def("copper", "Медь", Category.SOLIDS, Phase.SOLID, 0xFFC86A2A.toInt()) {
        strength = 11; conductivity = 0.8f; heatCapacity = 0.4f; conductive = true
        meltsAt(1085f, "molten_metal")
    }

    val GLASS = def("glass", "Стекло", Category.SOLIDS, Phase.SOLID, 0xFFC8E4EE.toInt()) {
        strength = 3; conductivity = 0.12f; heatCapacity = 0.8f; alpha = 165; acidResist = 0.95f
        meltsAt(1700f, "molten_glass")
    }

    val ICE = def("ice", "Лёд", Category.SOLIDS, Phase.SOLID, 0xFFB6E4FF.toInt()) {
        strength = 3; conductivity = 0.28f; heatCapacity = 0.55f; alpha = 210
        spawnsAt(-12f)
        meltsAt(1f, "water")
    }

    val OBSIDIAN = def("obsidian", "Обсидиан", Category.SOLIDS, Phase.SOLID, 0xFF2A1E38.toInt()) {
        strength = 16; conductivity = 0.1f; heatCapacity = 0.85f; acidResist = 0.95f
        meltsAt(1100f, "lava")
    }

    val BRICK = def("brick", "Кирпич", Category.SOLIDS, Phase.SOLID, 0xFFB0563A.toInt()) {
        strength = 7; conductivity = 0.14f; heatCapacity = 0.85f
    }

    val CONCRETE = def("concrete", "Бетон", Category.SOLIDS, Phase.SOLID, 0xFF9A9A92.toInt()) {
        strength = 10; conductivity = 0.15f; heatCapacity = 0.9f
    }

    val WIRE = def("wire", "Провод", Category.SOLIDS, Phase.SOLID, 0xFFB0793C.toInt()) {
        strength = 6; conductivity = 0.68f; heatCapacity = 0.4f; conductive = true
        meltsAt(1200f, "molten_metal")
    }

    val FUSE = def("fuse", "Фитиль", Category.SOLIDS, Phase.SOLID, 0xFF9A8A52.toInt()) {
        strength = 2; conductivity = 0.1f; heatCapacity = 0.5f
        burnsAt(160f, null, 420, 700f)
    }

    val TNT = def("tnt", "ТНТ", Category.SOLIDS, Phase.SOLID, 0xFFC23030.toInt()) {
        strength = 4; conductivity = 0.1f; heatCapacity = 0.7f
        burnsAt(200f, null, 10, 1400f)
        detonates(11f)
    }

    val C4 = def("c4", "C4", Category.SOLIDS, Phase.SOLID, 0xFFE2D888.toInt()) {
        strength = 5; conductivity = 0.1f; heatCapacity = 0.7f
        burnsAt(360f, null, 8, 1600f)
        detonates(17f)
    }

    // ------------------------------------------------------------------ life --
    val SEED = def("seed", "Семя", Category.LIFE, Phase.LIFE, 0xFFC89B3C.toInt()) {
        density = 130; strength = 1; conductivity = 0.06f
        burnsAt(220f, "ash", 200, 600f)
    }

    val PLANT = def("plant", "Растение", Category.LIFE, Phase.LIFE, 0xFF2E9B3E.toInt()) {
        strength = 1; jitter = 14; conductivity = 0.08f; heatCapacity = 0.8f
        burnsAt(250f, "ash", 320, 750f)
    }

    val FUNGUS = def("fungus", "Плесень", Category.LIFE, Phase.LIFE, 0xFF7E6AB0.toInt()) {
        strength = 1; jitter = 12; conductivity = 0.08f
        burnsAt(200f, "ash", 200, 600f)
    }

    val VIRUS = def("virus", "Вирус", Category.LIFE, Phase.LIFE, 0xFFC03AD0.toInt()) {
        strength = 1; jitter = 16; conductivity = 0.1f
        decaysInto(1600, null)
    }

    // ---------------------------------------------------------------- energy --
    val FIRE = def("fire", "Огонь", Category.ENERGY, Phase.ENERGY, 0xFFFF7020.toInt()) {
        density = 3; rises = true; conductivity = 0.25f; heatCapacity = 0.2f; dispersion = 1
        alpha = 210; glow = 255; spawnsAt(900f)
        decaysInto(26, null)
    }

    val SPARK = def("spark", "Искра", Category.ENERGY, Phase.ENERGY, 0xFF9FE8FF.toInt()) {
        density = 60; conductivity = 0.5f; heatCapacity = 0.2f
        alpha = 235; glow = 230; spawnsAt(320f)
        decaysInto(22, null)
        strength = 20
    }

    val LIGHTNING = def("lightning", "Молния", Category.ENERGY, Phase.ENERGY, 0xFFF0F8FF.toInt()) {
        density = 20; conductivity = 0.9f; heatCapacity = 0.15f
        alpha = 245; glow = 255; spawnsAt(3000f)
        decaysInto(9, null)
        strength = 20
        detonates(5f)
    }

    val PLASMA = def("plasma", "Плазма", Category.ENERGY, Phase.ENERGY, 0xFFFFC0FF.toInt()) {
        density = 2; rises = true; conductivity = 0.7f; heatCapacity = 0.25f; dispersion = 1
        alpha = 200; glow = 250; spawnsAt(4000f)
        decaysInto(90, "fire")
    }

    // -------------------------------------------------------------- machines --
    val CLONER = def("cloner", "Клонер", Category.SPECIAL, Phase.SPECIAL, 0xFF30D0C0.toInt()) {
        indestructible = true; strength = 20; conductivity = 0.1f; glow = 60
    }

    val VOID = def("void", "Аннигилятор", Category.SPECIAL, Phase.SPECIAL, 0xFF16161E.toInt()) {
        indestructible = true; strength = 20; conductivity = 0.02f
    }

    val WATER_TAP = def("water_tap", "Кран", Category.SPECIAL, Phase.SPECIAL, 0xFF2F8FE8.toInt()) {
        indestructible = true; strength = 20; conductivity = 0.3f; glow = 40
    }

    val LAVA_VENT = def("lava_vent", "Жерло вулкана", Category.SPECIAL, Phase.SPECIAL, 0xFFD04010.toInt()) {
        indestructible = true; strength = 20; conductivity = 0.4f; glow = 210
    }

    val HEATER = def("heater", "Нагреватель", Category.SPECIAL, Phase.SPECIAL, 0xFFE06040.toInt()) {
        indestructible = true; strength = 20; conductivity = 0.75f; glow = 220
        emitsHeat(420f)
    }

    val FREEZER = def("freezer", "Морозильник", Category.SPECIAL, Phase.SPECIAL, 0xFF60C8F0.toInt()) {
        indestructible = true; strength = 20; conductivity = 0.75f; glow = 120
        emitsHeat(-60f)
    }

    val BATTERY = def("battery", "Батарея", Category.SPECIAL, Phase.SPECIAL, 0xFFE8C830.toInt()) {
        indestructible = true; strength = 20; conductivity = 0.5f; glow = 90
    }

    /**
     * Immutable id-indexed table. Resolved lazily so that element definitions may point at
     * materials declared later in this object.
     */
    val ALL: Array<Element> by lazy {
        for (e in defs) {
            e.meltInto = id(e.meltKey)
            e.boilInto = id(e.boilKey)
            e.freezeInto = id(e.freezeKey)
            e.burnInto = id(e.burnKey)
            e.decayInto = id(e.decayKey)
        }
        defs.toTypedArray()
    }

    val COUNT: Int by lazy { ALL.size }

    private fun id(key: String?): Int =
        if (key == null) EMPTY.id else defs.firstOrNull { it.key == key }?.id
            ?: throw IllegalArgumentException("unknown element key: $key")

    fun byKey(key: String): Element =
        defs.firstOrNull { it.key == key } ?: throw IllegalArgumentException("unknown element key: $key")

    fun maybeByKey(key: String): Element? = defs.firstOrNull { it.key == key }

    /** Elements offered in the palette (everything except "empty"). */
    val PALETTE: List<Element> by lazy { ALL.filter { it.id != EMPTY.id } }
}
