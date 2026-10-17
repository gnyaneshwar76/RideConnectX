package com.eshwar.rideconnectx.domain.model

/**
 * The City picker's list (N15): bundled, so it works offline and costs
 * nothing. State capitals, and cities of about a lakh or more people in each
 * state. A city missing here can still be kept if it was saved earlier.
 */
object IndianCities {
    /** State → its cities, for the State → City → centre picker. */
    val byState: Map<String, List<String>> = sortedMapOf(
        "Andhra Pradesh" to listOf(
            "Anantapur", "Eluru", "Guntur", "Kadapa", "Kakinada", "Kurnool", "Nellore", "Ongole",
            "Rajahmundry", "Tirupati", "Vijayawada", "Visakhapatnam",
        ),
        "Arunachal Pradesh" to listOf("Itanagar"),
        "Assam" to listOf("Dibrugarh", "Guwahati"),
        "Bihar" to listOf("Bhagalpur", "Darbhanga", "Gaya", "Muzaffarpur", "Patna"),
        "Chandigarh" to listOf("Chandigarh"),
        "Chhattisgarh" to listOf("Bhilai", "Bilaspur", "Durg", "Korba", "Raipur"),
        "Delhi" to listOf("Delhi"),
        "Goa" to listOf("Panaji"),
        "Gujarat" to listOf(
            "Ahmedabad", "Anand", "Bhavnagar", "Gandhinagar", "Jamnagar", "Junagadh", "Rajkot",
            "Surat", "Vadodara",
        ),
        "Haryana" to listOf("Ambala", "Faridabad", "Gurugram", "Hisar", "Karnal", "Panipat", "Rohtak"),
        "Himachal Pradesh" to listOf("Shimla"),
        "Jammu and Kashmir" to listOf("Jammu", "Srinagar"),
        "Jharkhand" to listOf("Bokaro", "Dhanbad", "Jamshedpur", "Ranchi"),
        "Karnataka" to listOf(
            "Belgaum", "Bengaluru", "Davanagere", "Dharwad", "Gulbarga", "Hubli", "Mangaluru", "Mysuru",
        ),
        "Kerala" to listOf("Kochi", "Kollam", "Kozhikode", "Thiruvananthapuram", "Thrissur"),
        "Madhya Pradesh" to listOf("Bhopal", "Gwalior", "Indore", "Jabalpur", "Ujjain"),
        "Maharashtra" to listOf(
            "Ahmednagar", "Akola", "Amravati", "Aurangabad", "Bhiwandi", "Jalgaon", "Kalyan", "Kolhapur",
            "Latur", "Malegaon", "Mumbai", "Nagpur", "Nanded", "Nashik", "Navi Mumbai", "Pune", "Sangli",
            "Solapur", "Thane", "Vasai-Virar",
        ),
        "Manipur" to listOf("Imphal"),
        "Meghalaya" to listOf("Shillong"),
        "Mizoram" to listOf("Aizawl"),
        "Nagaland" to listOf("Kohima"),
        "Odisha" to listOf("Bhubaneswar", "Cuttack", "Rourkela"),
        "Puducherry" to listOf("Puducherry"),
        "Punjab" to listOf("Amritsar", "Jalandhar", "Ludhiana", "Patiala"),
        "Rajasthan" to listOf("Ajmer", "Alwar", "Bharatpur", "Bikaner", "Jaipur", "Jodhpur", "Kota", "Udaipur"),
        "Sikkim" to listOf("Gangtok"),
        "Tamil Nadu" to listOf(
            "Chennai", "Coimbatore", "Erode", "Madurai", "Salem", "Thanjavur", "Tiruchirappalli",
            "Tirunelveli", "Tiruppur", "Vellore",
        ),
        "Telangana" to listOf("Hyderabad", "Karimnagar", "Nizamabad", "Secunderabad", "Warangal"),
        "Tripura" to listOf("Agartala"),
        "Uttar Pradesh" to listOf(
            "Agra", "Aligarh", "Allahabad", "Bareilly", "Firozabad", "Ghaziabad", "Gorakhpur", "Jhansi",
            "Kanpur", "Lucknow", "Mathura", "Meerut", "Moradabad", "Noida", "Saharanpur", "Varanasi",
        ),
        "Uttarakhand" to listOf("Dehradun", "Haldwani"),
        "West Bengal" to listOf("Asansol", "Durgapur", "Kolkata", "Siliguri"),
    )

    val states: List<String> get() = byState.keys.toList()

    fun citiesIn(state: String): List<String> = byState[state].orEmpty()

    /** The state a listed city is in; null for a city not on the list. */
    fun stateOf(city: String): String? =
        byState.entries.firstOrNull { (_, cities) -> cities.any { it.equals(city.trim(), ignoreCase = true) } }?.key

    /** Every city, A to Z. */
    val all: List<String> = byState.values.flatten().sorted()

    /** Names starting with [query] first, then names containing it. */
    fun search(query: String, limit: Int = 8): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return all.take(limit)
        val starts = all.filter { it.startsWith(q, ignoreCase = true) }
        val contains = all.filter { it.contains(q, ignoreCase = true) && it !in starts }
        return (starts + contains).take(limit)
    }

    /** Case-insensitive match against the list, giving its spelling back. */
    fun find(name: String): String? = all.firstOrNull { it.equals(name.trim(), ignoreCase = true) }
}
