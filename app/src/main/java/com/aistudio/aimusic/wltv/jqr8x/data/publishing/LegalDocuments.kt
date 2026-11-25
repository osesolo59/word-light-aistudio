package com.aistudio.aimusic.wltv.jqr8x.data.publishing

/**
 * Complete legal disclosures for Word & Light AI STUDIO (Christians Strong Foundation Ministry).
 * Rendered locally in Settings when public HTTPS Privacy Policy / Terms URLs are not yet configured
 * or awaiting domain confirmation.
 */
object LegalDocuments {

    const val MINISTRY_OPERATOR = "Christians Strong Foundation Ministry"
    const val APP_NAME = "Word & Light AI STUDIO"
    const val SUPPORT_PHONE = "+233245200758"

    val privacyPolicyText: String = """
        $APP_NAME — Privacy Policy
        Operated by: $MINISTRY_OPERATOR

        1. Information We Collect & Use
        • Account Identity: When you sign in with Google via Android Credential Manager and Firebase Authentication, we receive your Firebase User ID, display name, email address, and profile photo URL solely to sync your ministry workspace and creative projects.
        • User-Created Media: Audio mixes, lyrics, images, and exported MP4 videos remain on your device unless you explicitly save project metadata to your private account or choose to publish a video to a linked channel.
        • Connected Publishing Channels (Optional):
          - YouTube: Uses Google Play Services AuthorizationClient with only the upload scope (https://www.googleapis.com/auth/youtube.upload).
          - Facebook Pages: Uses Meta OAuth 2.0 + PKCE with Page permissions (pages_show_list, pages_read_engagement, pages_manage_posts) exclusively to upload videos to the specific Facebook Page you choose.
          - TikTok: Uses TikTok OAuth 2.0 + PKCE (user.info.basic, video.publish) exclusively to query creator posting constraints and upload videos you initiate.

        2. Zero Passwords & Hardware-Encrypted Storage
        • We never request, collect, or store your YouTube, Facebook, or TikTok passwords.
        • OAuth access tokens and session metadata on Android are encrypted at rest with Android Keystore AES-256-GCM and excluded from Android cloud backup and device transfer.
        • Platform client secrets are never bundled in this Android app; when a broker backend is configured, client secrets reside exclusively inside encrypted server environment secrets.

        3. Data Sharing & Third-Party Services
        • We do not sell user data or share tokens with third-party advertisers.
        • Video files and captions you submit for direct publishing are transmitted directly from your device to the official YouTube Data API, Meta Graph API, or TikTok Content Posting API endpoints you authorized.

        4. Revocation, Disconnect & Account Deletion
        • You can disconnect any YouTube, Facebook Page, or TikTok session immediately from Settings -> Direct Video Publishing (OAuth).
        • You can also revoke third-party access directly from your Google Account, Facebook Business Integrations, or TikTok Security Settings, or request deletion by contacting Ministry Support at $SUPPORT_PHONE.
    """.trimIndent()

    val termsOfServiceText: String = """
        $APP_NAME — Terms of Service
        Operated by: $MINISTRY_OPERATOR

        1. Acceptance of Terms
        By using $APP_NAME, including its Gospel Audio Mixer, Video Studio, Music Video Creator, and optional Direct Social Media Publishing tools, you agree to these Terms of Service and the terms of any connected third-party platform.

        2. Content Rights & Ministry Broadcast Responsibility
        • You confirm that you own or hold all necessary music, scripture, visual, and broadcast rights for every audio track, image, caption, and exported MP4 video you publish.
        • You remain solely responsible for content visibility selections (Private/Draft, Unlisted/Followers, or Public) and compliance with YouTube Terms of Service, Meta Platform Terms, and TikTok Developer Terms.

        3. Platform Review & Visibility Limits
        • Unverified or sandbox platform integrations may enforce private-only visibility limits (such as TikTok SELF_ONLY sandbox mode or YouTube private upload locks for unverified projects) until official platform review completes.

        4. Strict Free Mode & Service Availability
        • Audio/video creation and Media3 local MP4 exports run locally on your device. No paid subscription is billed through this application.
        • We may update features or suspend third-party publishing integrations if platform policies or API access change.

        5. Contact
        For ministry support or legal inquiries, contact $MINISTRY_OPERATOR at $SUPPORT_PHONE.
    """.trimIndent()
}
