package com.elysium.console.data.util

import com.elysium.console.domain.model.RomFile
import java.util.Locale

/**
 * Intelligent Metadata Engine for Omni Elysium.
 * Uses fuzzy string matching to associate ROMs with cinematic boxart and descriptions.
 */
object MetadataScraper {

    data class ScrapedMetadata(
        val coverUrl: String,
        val year: String,
        val genre: String,
        val description: String
    )

    private val OMNI_DATABASE = mapOf(
        "super mario world" to ScrapedMetadata(
            "https://raw.githubusercontent.com/libretro/libretro-thumbnails/master/Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/Named_Boxarts/Super%20Mario%20World%20(USA).png",
            "1990", "Platformer", "Mario's first 16-bit adventure in the Dinosaur Land."
        ),
        "the legend of zelda a link to the past" to ScrapedMetadata(
            "https://raw.githubusercontent.com/libretro/libretro-thumbnails/master/Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/Named_Boxarts/Legend%20of%20Zelda%2C%20The%20-%20A%20Link%20to%20the%20Past%20(USA).png",
            "1991", "Action-Adventure", "Link travels between Light and Dark worlds to save Hyrule."
        ),
        "metroid dread" to ScrapedMetadata(
            "https://assets.nintendo.com/image/upload/ar_16:9,c_lpad,w_1200/b_white/f_auto/q_auto/v1/ncom/en_US/games/switch/m/metroid-dread-switch/hero",
            "2021", "Action", "Samus Aran's direct sequel to Metroid Fusion."
        ),
        "final fantasy vii" to ScrapedMetadata(
            "https://raw.githubusercontent.com/libretro/libretro-thumbnails/master/Sony%20-%20PlayStation/Named_Boxarts/Final%20Fantasy%20VII%20(USA).png",
            "1997", "RPG", "Cloud Strife joins AVALANCHE to take down Shinra."
        ),
        "crash bandicoot" to ScrapedMetadata(
            "https://raw.githubusercontent.com/libretro/libretro-thumbnails/master/Sony%20-%20PlayStation/Named_Boxarts/Crash%20Bandicoot%20(USA).png",
            "1996", "Platformer", "Help Crash stop Cortex's world-domination plans."
        ),
        "god of war" to ScrapedMetadata(
            "https://image.api.playstation.com/vulcan/img/rnd/202010/2217/L8Abbl7MvKPSVvC207n840An.png",
            "2005", "Action", "Kratos seeks revenge against the gods of Olympus."
        )
    )

    /**
     * Attempts to find a metadata match for the given ROM.
     */
    fun enrich(rom: RomFile): RomFile {
        val cleanName = rom.name.lowercase(Locale.ROOT)
            .replace(Regex("\\(.*?\\)"), "") // Remove (USA), (Disc 1), etc.
            .trim()

        // Exact match search
        val match = OMNI_DATABASE[cleanName] ?: OMNI_DATABASE.entries.find { 
            cleanName.contains(it.key) || it.key.contains(cleanName) 
        }?.value

        return if (match != null) {
            rom.copy(
                coverArtPath = match.coverUrl,
                year = match.year,
                genre = match.genre,
                description = match.description
            )
        } else {
            // Fallback: Generate a placeholder color or generic icon if no match found
            rom
        }
    }
}
