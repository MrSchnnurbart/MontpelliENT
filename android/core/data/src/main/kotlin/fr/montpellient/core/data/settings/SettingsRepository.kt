package fr.montpellient.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.montpellient.core.model.AppLanguage
import fr.montpellient.core.model.AppSettings
import fr.montpellient.core.model.ContrastLevel
import fr.montpellient.core.model.HomeCard
import fr.montpellient.core.model.SessionMode
import fr.montpellient.core.model.ThemeMode
import fr.montpellient.core.model.ThemePalette
import fr.montpellient.core.model.TopLevelDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Réglages persistés dans DataStore (non sensibles : aucun identifiant ici). */
class DataStoreSettingsRepository(context: Context) : SettingsRepository {

    private val store = context.applicationContext.settingsStore

    override val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs.write(updated)
        }
    }

    private object Keys {
        val session = stringPreferencesKey("session")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val palette = stringPreferencesKey("palette")
        val customSeed = longPreferencesKey("custom_seed")
        val amoled = booleanPreferencesKey("amoled")
        val contrast = stringPreferencesKey("contrast")
        val language = stringPreferencesKey("language")
        val tabs = stringPreferencesKey("tabs")
        val homeCards = stringPreferencesKey("home_cards")
        val hiddenHomeCards = stringPreferencesKey("hidden_home_cards")
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            session = enumOrDefault(this[Keys.session], defaults.session),
            themeMode = enumOrDefault(this[Keys.themeMode], defaults.themeMode),
            dynamicColor = this[Keys.dynamicColor] ?: defaults.dynamicColor,
            palette = enumOrDefault(this[Keys.palette], defaults.palette),
            customSeedColor = this[Keys.customSeed] ?: defaults.customSeedColor,
            amoledBlack = this[Keys.amoled] ?: defaults.amoledBlack,
            contrast = enumOrDefault(this[Keys.contrast], defaults.contrast),
            language = enumOrDefault(this[Keys.language], defaults.language),
            tabs = SettingsCodec.decodeTabs(this[Keys.tabs]) ?: defaults.tabs,
            homeCards = SettingsCodec.decodeCards(this[Keys.homeCards]) ?: defaults.homeCards,
            hiddenHomeCards = SettingsCodec.decodeCards(this[Keys.hiddenHomeCards])?.toSet()
                ?: defaults.hiddenHomeCards,
        )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.write(s: AppSettings) {
        this[Keys.session] = s.session.name
        this[Keys.themeMode] = s.themeMode.name
        this[Keys.dynamicColor] = s.dynamicColor
        this[Keys.palette] = s.palette.name
        this[Keys.customSeed] = s.customSeedColor
        this[Keys.amoled] = s.amoledBlack
        this[Keys.contrast] = s.contrast.name
        this[Keys.language] = s.language.name
        this[Keys.tabs] = SettingsCodec.encode(s.tabs)
        this[Keys.homeCards] = SettingsCodec.encode(s.homeCards)
        this[Keys.hiddenHomeCards] = SettingsCodec.encode(s.hiddenHomeCards.toList())
    }
}

private inline fun <reified E : Enum<E>> enumOrDefault(raw: String?, default: E): E =
    raw?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

/** Encodage simple « A,B,C » des listes d'énumérations, tolérant aux valeurs inconnues. */
object SettingsCodec {
    fun encode(values: List<Enum<*>>): String = values.joinToString(",") { it.name }

    fun decodeTabs(raw: String?): List<TopLevelDestination>? =
        decode<TopLevelDestination>(raw)?.distinct()?.take(AppSettings.MAX_TABS)

    /** Les cartes absentes (nouvelles versions de l'appli) sont ajoutées à la fin. */
    fun decodeCards(raw: String?): List<HomeCard>? = decode<HomeCard>(raw)?.distinct()

    fun normalizeCardOrder(cards: List<HomeCard>): List<HomeCard> =
        cards.distinct() + HomeCard.entries.filterNot { it in cards }

    private inline fun <reified E : Enum<E>> decode(raw: String?): List<E>? {
        if (raw == null) return null
        if (raw.isEmpty()) return emptyList()
        return raw.split(',').mapNotNull { name -> enumValues<E>().firstOrNull { it.name == name } }
    }
}

/** Fonctions utilitaires sur les réglages, testables sans Android. */
object SettingsRules {
    /** Active/désactive un onglet en respectant la limite [AppSettings.MAX_TABS]. */
    fun toggleTab(current: List<TopLevelDestination>, tab: TopLevelDestination): List<TopLevelDestination> =
        when {
            tab in current -> if (current.size > 1) current - tab else current
            current.size >= AppSettings.MAX_TABS -> current
            else -> current + tab
        }

    fun move(list: List<HomeCard>, from: Int, to: Int): List<HomeCard> {
        if (from !in list.indices || to !in list.indices) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun withDefaults(themeMode: ThemeMode = ThemeMode.SYSTEM) = AppSettings(themeMode = themeMode)

    fun resetAppearance(s: AppSettings): AppSettings = s.copy(
        themeMode = ThemeMode.SYSTEM,
        dynamicColor = true,
        palette = ThemePalette.UM,
        customSeedColor = ThemePalette.UM.seed,
        amoledBlack = false,
        contrast = ContrastLevel.STANDARD,
    )

    fun signOut(s: AppSettings): AppSettings = s.copy(session = SessionMode.NONE)

    fun languageOrSystem(tag: String?): AppLanguage =
        AppLanguage.entries.firstOrNull { it.tag == tag && it != AppLanguage.SYSTEM } ?: AppLanguage.SYSTEM
}
