package com.elysium.console.data.util

import com.elysium.console.domain.model.RomFile
import java.util.Locale

/**
 * Intelligent Metadata Engine for Omni Elysium.
 * Uses fuzzy string matching to associate ROMs with cinematic boxart and descriptions.
 */
object MetadataScraper {

    data class ScrapedMetadata(
        val name: String,
        val coverUrl: String,
        val year: String,
        val genre: String,
        val description: String,
        val matchers: List<String> = emptyList()
    )

    private var cachedDatabase: List<ScrapedMetadata>? = null

    /**
     * Initializes the scraper with a JSON database of games.
     */
    fun loadDatabase(json: String) {
        try {
            val jsonObject = org.json.JSONObject(json)
            val gamesArray = jsonObject.getJSONArray("games")
            val list = mutableListOf<ScrapedMetadata>()
            for (i in 0 until gamesArray.length()) {
                val obj = gamesArray.getJSONObject(i)
                val matchersList = mutableListOf<String>()
                val matchersArr = obj.getJSONArray("matchers")
                for (j in 0 until matchersArr.length()) {
                    matchersList.add(matchersArr.getString(j))
                }
                list.add(
                    ScrapedMetadata(
                        name = obj.getString("name"),
                        coverUrl = obj.getString("coverUrl"),
                        year = obj.getString("year"),
                        genre = obj.getString("genre"),
                        description = obj.getString("description"),
                        matchers = matchersList
                    )
                )
            }
            cachedDatabase = list
        } catch (e: Exception) {
            // Log error
        }
    }

    /**
     * Attempts to find a metadata match for the given ROM via fuzzy searching within the local DB.
     */
    fun enrich(rom: RomFile): RomFile {
        val db = cachedDatabase ?: return rom
        val cleanName = rom.name.lowercase(Locale.ROOT)
            .replace(Regex("\\(.*?\\)"), "") 
            .replace(Regex("\\[.*?\\]"), "")
            .trim()

        val match = db.find { game ->
            game.matchers.any { matcher ->
                cleanName.contains(matcher) || matcher.contains(cleanName)
            }
        }

        return if (match != null) {
            rom.copy(
                name = match.name,
                coverArtPath = match.coverUrl,
                year = match.year,
                genre = match.genre,
                description = match.description
            )
        } else {
            rom
        }
    }
}
