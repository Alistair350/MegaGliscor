package states.fieldstates

enum class WeatherType {
    NONE,
    SUN,
    RAIN,
    SAND,
    HAIL,
    SNOW,
}

data class WeatherState(
    var type: WeatherType = WeatherType.NONE,
    var turnsRemaining: Int = 0,
)
