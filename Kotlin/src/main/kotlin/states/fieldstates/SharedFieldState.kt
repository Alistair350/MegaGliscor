package states.fieldstates

data class SharedFieldState(
    var weather: WeatherState,
    var terrain: TerrainState,
    private val fieldEffects: MutableList<SharedFieldEffectState> = mutableListOf(),
) {
    fun hasEffect(type: Class<out SharedFieldEffectState>): Boolean =
        fieldEffects.any {
            it::class.java == type
        }

    fun addEffect(effect: SharedFieldEffectState) {
        removeEffect(effect::class.java)
        fieldEffects.add(effect)
    }

    fun removeEffect(type: Class<out SharedFieldEffectState>) {
        fieldEffects.removeIf {
            it::class.java == type
        }
    }

    fun getEffects(): List<SharedFieldEffectState> = fieldEffects
}
