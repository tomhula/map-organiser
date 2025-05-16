package io.github.tomhula

import kotlinx.serialization.Serializable

@Serializable
data class NominatimPlace(
    val place_id: Long,
    val licence: String,
    val osm_type: String,
    val osm_id: Long,
    val boundingbox: List<String>,
    val lat: String,
    val lon: String,
    val display_name: String,
    val `class`: String,
    val type: String,
    val importance: Double,
    val icon: String?,
    val address: Address?,
    val extratags: ExtraTags?
) {
    @Serializable
    data class Address(
        val city: String?,
        val city_district: String?,
        val municipality: String?,
        val town: String?,
        val village: String?,
        val state: String?,
        val `ISO3166-2-lvl4`: String?,
        val postcode: String?,
        val country: String?,
        val country_code: String?
    )

    @Serializable
    data class ExtraTags(
        val capital: String?,
        val website: String?,
        val wikidata: String?,
        val wikipedia: String?,
        val population: String?
    )
}
