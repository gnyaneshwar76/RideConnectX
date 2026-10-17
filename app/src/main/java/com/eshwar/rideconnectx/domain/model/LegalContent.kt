package com.eshwar.rideconnectx.domain.model

/** One numbered section of a legal document. */
data class LegalSection(
    val title: String,
    val body: String,
    val bullets: List<String> = emptyList(),
    val tail: String = "",
)

/** Which document the legal screen is showing. */
enum class LegalDoc(val screenTitle: String) {
    TERMS("Terms & Conditions"),
    PRIVACY("Privacy Policy"),

    /**
     * Both documents on one page. This is what the profile step links to —
     * sending a rider to two separate pages to agree to one checkbox is
     * needless friction.
     */
    BOTH("Terms & Privacy Policy"),
}

/**
 * The real Terms of Service and Privacy Policy, supplied by the RideConnectX
 * team. Kept as data rather than strings.xml so the screen can render the
 * numbered / bulleted structure faithfully.
 */
object LegalContent {

    fun sections(doc: LegalDoc): List<LegalSection> = when (doc) {
        LegalDoc.TERMS -> TERMS
        LegalDoc.PRIVACY -> PRIVACY
        LegalDoc.BOTH -> TERMS + PRIVACY
    }

    val TERMS = listOf(
        LegalSection(
            "1. Acceptance of Terms",
            "Welcome to RideConnectX. By downloading, installing, or using the application, you agree to these Terms of Service. If you do not agree with these terms, please discontinue use of the application.",
        ),
        LegalSection(
            "2. About RideConnectX",
            "RideConnectX is a companion application designed to enhance compatible vehicles from any vehicle manufacturer by providing navigation, ride information, connectivity features, and personalization tools.",
            tail = "RideConnectX is an independent application and is not affiliated with, endorsed by, or sponsored by any vehicle manufacturer or any of its subsidiaries.",
        ),
        LegalSection(
            "3. Eligibility",
            "You must comply with all applicable laws while using RideConnectX.",
            tail = "You are responsible for ensuring that your use of the application is legal in your region.",
        ),
        LegalSection(
            "4. Safe Riding",
            "Your safety is your responsibility. RideConnectX is intended to assist your riding experience but must never distract you while operating a vehicle.\n\nAlways:",
            bullets = listOf(
                "Focus on the road.",
                "Follow traffic laws.",
                "Wear appropriate safety equipment.",
                "Obey local regulations.",
            ),
            tail = "Do not interact with the application while riding unless it is safe to do so.",
        ),
        LegalSection(
            "5. Navigation",
            "Navigation guidance is provided for convenience only. Routes, estimated arrival times, traffic conditions, and turn instructions may occasionally be inaccurate or unavailable.",
            tail = "Always prioritize road signs, traffic signals, and local laws over information displayed by the application.",
        ),
        LegalSection(
            "6. Bluetooth Connectivity",
            "Vehicle connectivity depends on device compatibility, Bluetooth availability, operating system restrictions, and supported vehicle features.",
            tail = "RideConnectX does not guarantee uninterrupted or continuous connectivity.",
        ),
        LegalSection(
            "7. User Accounts",
            "Some features may require signing in.",
            tail = "You are responsible for maintaining the security of your account and any activity performed using your account.",
        ),
        LegalSection(
            "8. Intellectual Property",
            "RideConnectX, its branding, interface, graphics, icons, and original software are protected by applicable intellectual property laws.",
            tail = "You may not copy, modify, reverse engineer, redistribute, or commercially exploit the application except where permitted by law.",
        ),
        LegalSection(
            "9. Acceptable Use",
            "You agree not to:",
            bullets = listOf(
                "Misuse the application.",
                "Attempt unauthorized access.",
                "Interfere with application functionality.",
                "Use RideConnectX for illegal activities.",
                "Modify communication with vehicles in ways that violate applicable laws.",
            ),
        ),
        LegalSection(
            "10. Updates",
            "RideConnectX may receive updates that improve functionality, security, compatibility, or performance.",
            tail = "Some features may change or be removed over time.",
        ),
        LegalSection(
            "11. Disclaimer",
            "RideConnectX is provided \"as is\" without warranties of any kind.",
            tail = "While every effort is made to provide reliable functionality, uninterrupted operation cannot be guaranteed.",
        ),
        LegalSection(
            "12. Limitation of Liability",
            "To the maximum extent permitted by law, RideConnectX and its developers shall not be liable for indirect, incidental, consequential, or special damages arising from the use or inability to use the application.",
        ),
        LegalSection(
            "13. Changes to These Terms",
            "These Terms may be updated periodically.",
            tail = "Continued use of RideConnectX after updates constitutes acceptance of the revised Terms.",
        ),
        LegalSection(
            "14. Contact",
            "Questions regarding these Terms may be submitted through the official RideConnectX support channels.",
        ),
    )

    /**
     * Shown by the screen that lists service centres and by About. Required by
     * the OpenStreetMap licence (ODbL) wherever its data is shown.
     */
    const val OSM_CREDIT = "Service centre search uses map data © OpenStreetMap contributors, available under the Open Database Licence. openstreetmap.org/copyright"

    // Mirrors docs/PRIVACY-POLICY.md and site/public/privacy.html. Change all three together.
    val PRIVACY = listOf(
        LegalSection(
            "1. Introduction",
            "RideConnectX shows turn-by-turn directions, alerts and your name on your scooter's instrument cluster over Bluetooth. Last updated 10 October 2026.",
            tail = "This policy explains what the app collects, where it goes, and what stays on your phone. Nothing is sold, and nothing is shared with advertisers. The app has no analytics, no advertising and no advertising identifier. The Bluetooth link to the cluster is not encrypted: caller and sender names cross the air in plain text.",
        ),
        LegalSection(
            "2. Stays on your phone",
            "These are stored on your phone only and are tied to the account that created them:",
            bullets = listOf(
                "Emergency contacts you add",
                "Service and refuelling records",
                "Notification history on the Notifications page (caller or sender name and kind; never the message text). The same names are shown on the cluster",
                "Ride log, off by default; only if you switch it on in Settings (Ride testing): Maps instructions and destination text, caller or sender names (never message text) and route times, never uploaded; delete it with Clear ride log",
                "Paired scooter address and name",
                "Settings and appearance",
            ),
            tail = "Backup is disabled, so none of this is copied to Google cloud backup.",
        ),
        LegalSection(
            "3. Stored in your account",
            "If you sign in, this is saved to Firebase (Google Cloud) under your account, readable only by you:",
            bullets = listOf(
                "Name, email address and sign-in method",
                "Rider name, nickname and city",
                "Selected vehicle and colour",
                "Account dates and a default settings record created at first sign-in",
                "Profile photo, if you set one, at a reduced size",
                "Your scooter's last odometer, trip and fuel readings",
                "Rides you keep (maximum 10): start and end time, distance, duration, top and average speed, title, vehicle and the route (GPS points, precise location history).",
            ),
            tail = "While the scooter is connected the app records your ride route with GPS and saves it on the phone. A ride you do not keep is deleted automatically 30 days after it ended. Only a ride you keep while signed in is uploaded; deleting it removes the cloud copy. As a guest, nothing is uploaded.",
        ),
        LegalSection(
            "4. Permissions",
            "Each permission is used only for the feature named:",
            bullets = listOf(
                "Bluetooth: find and connect to your scooter.",
                "Notification access: read the Google Maps navigation notification to draw the next turn on the cluster, and read call and message notifications (caller or sender name, and message text for messages) to show alerts on the cluster. This is processed on the phone and sent only to your scooter. Message text is read only to count and alert; it is never stored or uploaded. A name with no Latin letters is shown as the fixed label \"New message\".",
                "Location: share your position when you tap share on SOS; fill in your city; count down the distance to the next turn while you navigate (on the phone only, not stored); record your ride route while the scooter is connected; find nearby service centres.",
                "Contacts: only when you pick an emergency contact. The app keeps that one name and number.",
                "Photos: only when you choose a profile picture.",
                "Phone state: read your mobile signal strength to show bars on the cluster. Calls, call log and your number are not read.",
                "Notifications: show the ongoing notification Android requires while connected.",
            ),
        ),
        LegalSection(
            "5. Other services",
            "The app uses these outside services:",
            bullets = listOf(
                "Firebase Authentication and Cloud Firestore (Google): your sign-in and the account data above, only when you sign in. Google Sign-In if you choose it.",
                "OpenStreetMap Overpass (overpass-api.de): when you look up service centres, your approximate position (rounded to about 1 km) is sent to find places nearby. The service also sees your IP address. Nothing is linked to your account.",
                "Firebase App Check (Google): confirms to Firebase that requests come from this app; no profile data.",
                "Android Geocoder: turns coordinates into a place name; on most phones Google's location service does this.",
            ),
            tail = OSM_CREDIT,
        ),
        LegalSection(
            "6. Security",
            "Reasonable measures protect your information: account data is readable only by you.",
            tail = "No electronic storage method is completely secure.",
        ),
        LegalSection(
            "7. Your choices",
            "You may:",
            bullets = listOf(
                "Use the app as a guest; nothing is uploaded.",
                "Sign out; your last readings are saved to your account and your profile is removed from the phone.",
                "Delete local data from the app; it removes rides, notifications, service records, emergency contacts, the paired scooter, cached readings, the ride log, your avatar and profile preferences.",
                "Delete your account in Profile; your cloud data and that account's data on the phone are deleted.",
                "Revoke any permission in Android Settings.",
            ),
        ),
        LegalSection(
            "8. Children's Privacy",
            "RideConnectX is not intended for children under the age required by applicable law.",
        ),
        LegalSection(
            "9. Policy Updates",
            "This policy may be updated periodically.",
            tail = "Material changes will be reflected by updating the \"Last updated\" date.",
        ),
        LegalSection(
            "10. Contact",
            "Questions regarding privacy: the project's GitHub Issues page. The full policy is at rideconnectx-89528dd3.web.app/privacy.html.",
        ),
    )
}
