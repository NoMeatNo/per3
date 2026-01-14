package com.likdev256

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import org.jsoup.Jsoup

/**
 * IranWiz Plus Provider - Persian Live TV from GLWiz + Other News Channels
 */
class IranWizPlusProvider : MainAPI() {
    override var mainUrl = "https://www.glwiz.com"
    override var name = "IranWiz Plus"
    override val hasMainPage = true
    override var lang = "fa"
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)
    
    // Additional New Channels Source
    private val otherNewsUrls = mapOf(
        "IranInternational" to listOf(
            "https://dev-live.livetvstream.co.uk/LS-63503-4/chunklist_b1196000.m3u8", // Direct
            "https://www.youtube.com/watch?v=A92pqZQAsm8" // YouTube
        ),
        "BBCPersian" to listOf(
            "https://vs-cmaf-pushb-ww.live.cf.md.bbci.co.uk/x=4/i=urn:bbc:pips:service:bbc_persian_tv/pc_hd_abr_v2.fmp4.m3u8", // Direct 1
            "https://vs-cmaf-pushb-ww-live.akamaized.net/x=4/i=urn:bbc:pips:service:bbc_persian_tv/pc_hd_abr_v2.fmp4.m3u8", // Direct 2
            "https://www.youtube.com/watch?v=5qN1dRjatv8" // YouTube
        ),
        "FoxNews" to listOf(
            "https://www.newslive.com/american/fox-news.html",
            "https://iptv-web.app/US/FoxNewsChannel.us/"
        ),
        "CNN" to listOf(

            "https://turnerlive.warnermediacdn.com/hls/live/586495/cnngo/cnn_slate/VIDEO_0_3564000.m3u8",
            "https://turnerlive.warnermediacdn.com/hls/live/586495/cnngo/cnn_slate/VIDEO_2_1964000.m3u8",
            "https://turnerlive.warnermediacdn.com/hls/live/586495/cnngo/cnn_slate/VIDEO_3_1464000.m3u8",
            "https://turnerlive.warnermediacdn.com/hls/live/586495/cnngo/cnn_slate/VIDEO_4_1064000.m3u8",
            "https://turnerlive.warnermediacdn.com/hls/live/586497/cnngo/cnni/VIDEO_0_3564000.m3u8",
            "https://www.newslive.com/american/cnn-stream.html"
        ),
        "CNNGB" to listOf("https://d2anxhed2mfixb.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-wqc602hxepp0q/CNNFAST_GB.m3u8"),
        "MSNBC" to listOf(
            "https://www.newslive.com/american/msnbc-news-live.html",
            "https://iptv-web.app/US/MSNBC.us/"
        ),
        "CNBC" to listOf("https://www.newslive.com/business/cnbc-live-free.html"),
        "ABCNews" to listOf("https://iptv-web.app/US/ABCNewsLive.us/"),
        "BloombergQuicktake" to listOf("https://iptv-web.app/US/BloombergQuicktake.us/"),
        "ABCNews" to listOf("https://iptv-web.app/US/ABCNewsLive.us/"),
        "BloombergQuicktake" to listOf("https://iptv-web.app/US/BloombergQuicktake.us/"),
        "NBCNews" to listOf("https://iptv-web.app/US/NBCNewsNOW.us/"),
        "NewsNation" to listOf("https://iptv-web.app/US/NewsNation.us/"),
        "CBSNews247" to listOf("https://iptv-web.app/US/CBSNews247.us/"),
        "ReutersTV" to listOf("https://iptv-web.app/US/ReutersTV.us/"),
        "CheddarNews" to listOf("https://iptv-web.app/US/CheddarNews.us/"),
        "ScrippsNews" to listOf("https://iptv-web.app/US/ScrippsNews.us/"),
        "WABCDT1247News" to listOf("https://iptv-web.app/US/WABCDT1247News.us/"),
        "KABCDT1247News" to listOf("https://iptv-web.app/US/KABCDT1247News.us/"),
        "CBSNewsNewYork" to listOf("https://iptv-web.app/US/CBSNewsNewYork.us/"),
        "CBSNewsLosAngeles" to listOf("https://iptv-web.app/US/CBSNewsLosAngeles.us/"),
        "NewsmaxTV" to listOf("https://iptv-web.app/US/NewsmaxTV.us/"),
        "TheYoungTurks" to listOf("https://iptv-web.app/US/TheYoungTurks.us/"),
         // Local Networks
        "KABCDT1" to listOf("https://iptv-web.app/US/KABCDT1.us/"),
        "KCBSDT1" to listOf("https://iptv-web.app/US/KCBSDT1.us/"),
        "KDFWDT1" to listOf("https://iptv-web.app/US/KDFWDT1.us/"),
        "KIRODT1" to listOf("https://iptv-web.app/US/KIRODT1.us/"),
        "KMBCDT1" to listOf("https://iptv-web.app/US/KMBCDT1.us/"),
        "KIRODT1" to listOf("https://iptv-web.app/US/KIRODT1.us/"),
        "KMBCDT1" to listOf("https://iptv-web.app/US/KMBCDT1.us/"),
        "KMEXDT1" to listOf("https://iptv-web.app/US/KMEXDT1.us/"),
        "KNBCDT1" to listOf("https://iptv-web.app/US/KNBCDT1.us/"),
        "KNTVDT1" to listOf("https://iptv-web.app/US/KNTVDT1.us/"),
        "KXASDT1" to listOf("https://iptv-web.app/US/KXASDT1.us/"),
        "KTTVDT1" to listOf("https://iptv-web.app/US/KTTVDT1.us/"),
        "KTVUDT1" to listOf("https://iptv-web.app/US/KTVUDT1.us/"),
        "KYWDT1" to listOf("https://iptv-web.app/US/KYWDT1.us/"),
        "WCVBDT1" to listOf("https://iptv-web.app/US/WCVBDT1.us/"),
        // Cooking
        "AmericasTestKitchen" to listOf("https://iptv-web.app/US/AmericasTestKitchen.us/"),
        "bonappetit" to listOf("https://iptv-web.app/US/bonappetit.us/"),
        "GordonRamsaysHellsKitchen" to listOf("https://iptv-web.app/US/GordonRamsaysHellsKitchen.us/"),
        "Tastemade" to listOf("https://iptv-web.app/US/Tastemade.us/"),
        // Documentary
        "CourtTV" to listOf("https://iptv-web.app/US/CourtTV.us/"),
        "Dateline247" to listOf("https://iptv-web.app/US/Dateline247.us/"),
        "ForensicFiles" to listOf("https://iptv-web.app/US/ForensicFiles.us/"),
        "History" to listOf("https://iptv-web.app/US/History.us/"),
        "InkMaster" to listOf("https://iptv-web.app/US/InkMaster.us/"),
        "LawCrime" to listOf("https://iptv-web.app/US/LawCrime.us/"),
        "MilitaryHistory" to listOf("https://iptv-web.app/US/MilitaryHistory.us/"),
        "NationalGeographic" to listOf("https://iptv-web.app/US/NationalGeographic.us/"),
        "NationalGeographicWild" to listOf("https://iptv-web.app/US/NationalGeographicWild.us/"),
        "PlutoTVCrime" to listOf("https://iptv-web.app/US/PlutoTVCrime.us/"),
        "Reelz" to listOf("https://iptv-web.app/US/Reelz.us/"),
        "PlutoTVHistory" to listOf("https://iptv-web.app/US/PlutoTVHistory.us/"),
        "PlutoTVInvestigation" to listOf("https://iptv-web.app/US/PlutoTVInvestigation.us/"),
        // Entertainment
        "AE" to listOf("https://iptv-web.app/US/AE.us/"),
        "StoriesbyAMC" to listOf("https://iptv-web.app/US/StoriesbyAMC.us/"),
        "BBCAmerica" to listOf("https://iptv-web.app/US/BBCAmerica.us/"),
        "BETHer" to listOf("https://iptv-web.app/US/BETHer.us/"),
        "BETPlutoTV" to listOf("https://iptv-web.app/US/BETPlutoTV.us/"),
        "E" to listOf("https://iptv-web.app/US/E.us/"),
        "FX" to listOf("https://iptv-web.app/US/FX.us/"),
        "FXX" to listOf("https://iptv-web.app/US/FXX.us/"),
        "Lifetime" to listOf("https://iptv-web.app/US/Lifetime.us/"),
        "MTV" to listOf("https://iptv-web.app/US/MTV.us/"),
        "MTV2" to listOf("https://iptv-web.app/US/MTV2.us/"),
        "MTVenEspanol" to listOf("https://iptv-web.app/US/MTVenEspanol.us/"),
        "MTVPlutoTV" to listOf("https://iptv-web.app/US/MTVPlutoTV.us/"),
        "MTVRidiculousness" to listOf("https://iptv-web.app/US/MTVRidiculousness.us/"),
        "Oxygen" to listOf("https://iptv-web.app/US/Oxygen.us/"),
        // Classics
        "AntennaTV" to listOf("https://iptv-web.app/US/AntennaTV.us/"),
        "CoziTV" to listOf("https://iptv-web.app/US/CoziTV.us/"),
        "Comet" to listOf("https://iptv-web.app/US/Comet.us/"),
        "Grit" to listOf("https://iptv-web.app/US/Grit.us/"),
        "GritXtra" to listOf("https://iptv-web.app/US/GritXtra.us/"),
        "IONTV" to listOf("https://iptv-web.app/US/IONTV.us/"),
        "IONPlus" to listOf("https://iptv-web.app/US/IONPlus.us/"),
        "MeTV" to listOf("https://iptv-web.app/US/MeTV.us/"),
        "Buzzr" to listOf("https://iptv-web.app/US/Buzzr.us/"),
        "GameShowNetwork" to listOf("https://iptv-web.app/US/GameShowNetwork.us/"),
        "GameShowNetwork" to listOf("https://iptv-web.app/US/GameShowNetwork.us/"),
        "ThePriceIsRightTheBarkerEra" to listOf("https://iptv-web.app/US/ThePriceIsRightTheBarkerEra.us/"),
        
        // Music (US)
        "MTVLive" to listOf("https://iptv-web.app/US/MTVLive.us/"),
        "CMT" to listOf("https://iptv-web.app/US/CMT.us/"),
        "AXSTV" to listOf("https://iptv-web.app/US/AXSTV.us/"),
        "VevoPop" to listOf("https://iptv-web.app/US/VevoPop.us/"),
        "Vevo80s" to listOf("https://iptv-web.app/US/Vevo80s.us/"),
        "Vevo90s" to listOf("https://iptv-web.app/US/Vevo90s.us/"),
        
        // Movies (US)
        "AMC" to listOf("https://iptv-web.app/US/AMC.us/"),
        "ParamountNetwork" to listOf("https://iptv-web.app/US/ParamountNetwork.us/"),
        "MGMPlus" to listOf("https://iptv-web.app/US/MGMPlus.us/"),
        "FXM" to listOf("https://iptv-web.app/US/FXM.us/"),
        "SundanceTV" to listOf("https://iptv-web.app/US/SundanceTV.us/"),
        "IFC" to listOf("https://iptv-web.app/US/IFC.us/"),
        "HallmarkMoviesMysteries" to listOf("https://iptv-web.app/US/HallmarkMoviesMysteries.us/"),
        "Movies" to listOf("https://iptv-web.app/US/Movies.us/"),
        "Charge" to listOf("https://iptv-web.app/US/Charge.us/"),
        
        // Series (US)
        "StarTrek" to listOf("https://iptv-web.app/US/StarTrek.us/"),
        "TheWalkingDeadUniverse" to listOf("https://iptv-web.app/US/TheWalkingDeadUniverse.us/"),
        "CSI" to listOf("https://iptv-web.app/US/CSI.us/"),
        "DoctorWhoClassic" to listOf("https://iptv-web.app/US/DoctorWhoClassic.us/"),
        "HellsKitchen" to listOf("https://iptv-web.app/US/HellsKitchen.us/"),
        "BarRescue" to listOf("https://iptv-web.app/US/BarRescue.us/"),
        "Cheers" to listOf("https://iptv-web.app/US/Cheers.us/"),
        "Frasier" to listOf("https://iptv-web.app/US/Frasier.us/"),
        "WalkerTexasRanger" to listOf("https://iptv-web.app/US/WalkerTexasRanger.us/"),
        "UnsolvedMysteries" to listOf("https://iptv-web.app/US/UnsolvedMysteries.us/"),
        "Cops" to listOf("https://iptv-web.app/US/Cops.us/"),
        "TheBobRossChannel" to listOf("https://iptv-web.app/US/TheBobRossChannel.us/"),
        "ThisOldHouse" to listOf("https://iptv-web.app/US/ThisOldHouse.us/"),
        
        // Sports (US)
        "NFLRedZone" to listOf("https://iptv-web.app/US/NFLRedZone.us/"),
        "NBATV" to listOf("https://iptv-web.app/US/NBATV.us/"),
        "MLBNetwork" to listOf("https://iptv-web.app/US/MLBNetwork.us/"),
        "NHLNetwork" to listOf("https://iptv-web.app/US/NHLNetwork.us/"),
        "CBSSportsNetworkUSA" to listOf("https://iptv-web.app/US/CBSSportsNetworkUSA.us/"),
        "GolfChannel" to listOf("https://iptv-web.app/US/GolfChannel.us/"),
        "TennisChannel" to listOf("https://iptv-web.app/US/TennisChannel.us/"),
        "ESPNU" to listOf("https://iptv-web.app/US/ESPNU.us/"),
        "CBSSportsGolazoNetwork" to listOf("https://iptv-web.app/US/CBSSportsGolazoNetwork.us/"),
        
        // Batch 7: Others
        "Showtime" to listOf("https://iptv-web.app/US/Showtime.us/"),
        "Starz" to listOf("https://iptv-web.app/US/Starz.us/"),
        "USANetwork" to listOf("https://iptv-web.app/US/USANetwork.us/"),
        "Syfy" to listOf("https://iptv-web.app/US/Syfy.us/"),
        "Bravo" to listOf("https://iptv-web.app/US/Bravo.us/"),
        "BigTenNetwork" to listOf("https://iptv-web.app/US/BigTenNetwork.us/"),
        "SECNetwork" to listOf("https://iptv-web.app/US/SECNetwork.us/"),
        "YesNetwork" to listOf("https://iptv-web.app/US/YesNetwork.us/"),
        "WABCDT1" to listOf("https://iptv-web.app/US/WABCDT1.us/"),
        "WCBSDT1" to listOf("https://iptv-web.app/US/WCBSDT1.us/"),
        "WNBCDT1" to listOf("https://iptv-web.app/US/WNBCDT1.us/"),
        "WNYWDT1" to listOf("https://iptv-web.app/US/WNYWDT1.us/"),
        "WGNDT1" to listOf("https://iptv-web.app/US/WGNDT1.us/"),
        
        // Batch 8: UK Channels
        // Note: UK channels are under /UK/ path
        "BBCOne" to listOf("https://iptv-web.app/UK/BBCOne.uk/"),
        "BBCTwo" to listOf("https://iptv-web.app/UK/BBCTwo.uk/"),
        "Channel4" to listOf("https://iptv-web.app/UK/Channel4.uk/"),
        "Channel5" to listOf("https://iptv-web.app/UK/Channel5.uk/"),
        "ITV2" to listOf("https://iptv-web.app/UK/ITV2.uk/"),
        "ITV3" to listOf("https://iptv-web.app/UK/ITV3.uk/"),
        "ITV4" to listOf("https://iptv-web.app/UK/ITV4.uk/"),
        "BBCNews" to listOf("https://iptv-web.app/UK/BBCNews.uk/"),
        "GBNews" to listOf("https://iptv-web.app/UK/GBNews.uk/"),
        "BBCEarth" to listOf("https://iptv-web.app/UK/BBCEarth.uk/"),
        "BBCFour" to listOf("https://iptv-web.app/UK/BBCFour.uk/"),
        "TalkTV" to listOf("https://iptv-web.app/UK/TalkTV.uk/"),
        "S4C" to listOf("https://iptv-web.app/UK/S4C.uk/"),
        "WildEarth" to listOf("https://iptv-web.app/UK/WildEarth.uk/"),
        
        // Batch 10: Italy (Direct M3U8s)
        "Rai1" to listOf("https://mediapolis.rai.it/relinker/relinkerServlet.htm?cont=2606803&output=16"),
        "Rai2" to listOf("https://mediapolis.rai.it/relinker/relinkerServlet.htm?cont=308718&output=16"),
        "Rai3" to listOf("https://mediapolis.rai.it/relinker/relinkerServlet.htm?cont=308709&output=16"),
        "RealTime" to listOf("https://d3562mgijzx0zq.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-kizqtzpvvl3i8/Realtime_IT.m3u8"),
        "DMAX" to listOf("https://d2j2nqgg7bzth.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-02k1gv1j0ufwn/DMAX_IT.m3u8"),
        "RaiNews24" to listOf("https://mediapolis.rai.it/relinker/relinkerServlet.htm?cont=1&output=16"),
        "RaiSport" to listOf("https://mediapolis.rai.it/relinker/relinkerServlet.htm?cont=358025&output=16"),
        "SportItalia" to listOf("https://tinyurl.com/p7yv8stc"),
        "SuperTennis" to listOf("https://live-embed.supertennix.hiway.media/restreamer/supertennix_client/gpu-a-c0-16/restreamer/outgest/aa3673f1-e178-44a9-a947-ef41db73211a/manifest.m3u8")
    )

    private val playerBaseUrl = "$mainUrl/Pages/Player"
    private val ajaxUrl = "$playerBaseUrl/Ajax.aspx"
    
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
    
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val host = url.host
                    cookieStore.getOrPut(host) { mutableListOf() }.apply {
                        cookies.forEach { newCookie -> removeAll { it.name == newCookie.name } }
                        addAll(cookies)
                    }
                }
                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    return cookieStore[url.host]?.filter { it.expiresAt >= System.currentTimeMillis() } ?: emptyList()
                }
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    // Genre IDs for categories
    companion object {
        const val GENRE_NEWS = 89
        const val GENRE_PERSIAN = 1
        const val GENRE_IRIB = 2
        const val GENRE_MOVIES = 19
        const val GENRE_MUSIC = 10
        const val GENRE_SPORTS = 84
        const val GENRE_KIDS = 4
        const val GENRE_RELIGIOUS = 85
        const val GENRE_OTHER_NEWS = 100
        const val GENRE_COOKING = 101
        const val GENRE_DOCUMENTARY = 102
        const val GENRE_ENTERTAINMENT = 103
        const val GENRE_CLASSICS = 104
        const val GENRE_MOVIES_US = 105
        const val GENRE_MUSIC_US = 106
        const val GENRE_SERIES_US = 107
        const val GENRE_SPORTS_US = 108
        const val GENRE_UK = 109
        const val GENRE_ITALY = 110
    }
    
    data class Channel(
        val streamName: String,
        val displayName: String,
        val id: Int,
        val genreId: Int
    ) {
        val logoUrl: String get() = if (id > 0) "https://hd200.glwiz.com/menu/epg/imagesNew/cim_${id}.png" else "https://raw.githubusercontent.com/NoMeatNo/per3/refs/heads/master/logos/tv.png"
    }
    
    // Channel list
    private val allChannels = listOf(
         // ===== Other News (Genre 100) =====
        Channel("FoxNews", "Fox News", 0, GENRE_OTHER_NEWS),
        Channel("CNN", "CNN", 0, GENRE_OTHER_NEWS),
        Channel("CNNGB", "CNN Fast", 0, GENRE_OTHER_NEWS),
        Channel("MSNBC", "MSNBC", 0, GENRE_OTHER_NEWS),
        Channel("CNBC", "CNBC", 0, GENRE_OTHER_NEWS),
        Channel("ABCNews", "ABC News Live", 0, GENRE_OTHER_NEWS),
        Channel("BloombergQuicktake", "Bloomberg Quicktake", 0, GENRE_OTHER_NEWS),
        Channel("NBCNews", "NBC News NOW", 0, GENRE_OTHER_NEWS),
        Channel("NewsNation", "NewsNation", 0, GENRE_OTHER_NEWS),
        Channel("CBSNews247", "CBS News 24/7", 0, GENRE_OTHER_NEWS),
        Channel("ReutersTV", "Reuters TV", 0, GENRE_OTHER_NEWS),
        Channel("CheddarNews", "Cheddar News", 0, GENRE_OTHER_NEWS),
        Channel("ScrippsNews", "Scripps News", 0, GENRE_OTHER_NEWS),
        Channel("WABCDT1247News", "ABC 7 NY 24/7", 0, GENRE_OTHER_NEWS),
        Channel("KABCDT1247News", "ABC 7 LA 24/7", 0, GENRE_OTHER_NEWS),
        Channel("CBSNewsNewYork", "CBS News New York", 0, GENRE_OTHER_NEWS),
        Channel("CBSNewsLosAngeles", "CBS News Los Angeles", 0, GENRE_OTHER_NEWS),
        Channel("NewsmaxTV", "Newsmax", 0, GENRE_OTHER_NEWS),
        Channel("TheYoungTurks", "The Young Turks", 0, GENRE_OTHER_NEWS),
        Channel("WAGA", "WAGA Fox 5", 0, GENRE_OTHER_NEWS),
        
        // ===== Local Networks (Genre 100) =====
        Channel("KABCDT1", "KABC Los Angeles (ABC)", 0, GENRE_OTHER_NEWS),
        Channel("WABCDT1", "WABC New York (ABC)", 0, GENRE_OTHER_NEWS),
        Channel("WCVBDT1", "WCVB Boston (ABC)", 0, GENRE_OTHER_NEWS),
        Channel("KMBCDT1", "KMBC Kansas City (ABC)", 0, GENRE_OTHER_NEWS),
        Channel("KCBSDT1", "KCBS Los Angeles (CBS)", 0, GENRE_OTHER_NEWS),
        Channel("WCBSDT1", "WCBS New York (CBS)", 0, GENRE_OTHER_NEWS),
        Channel("KYWDT1", "KYW Philadelphia (CBS)", 0, GENRE_OTHER_NEWS),
        Channel("KIRODT1", "KIRO Seattle (CBS)", 0, GENRE_OTHER_NEWS),
        Channel("KNBCDT1", "KNBC Los Angeles (NBC)", 0, GENRE_OTHER_NEWS),
        Channel("WNBCDT1", "WNBC New York (NBC)", 0, GENRE_OTHER_NEWS),
        Channel("KTTVDT1", "KTTV Los Angeles (FOX)", 0, GENRE_OTHER_NEWS),
        Channel("KDFWDT1", "KDFW Dallas (FOX)", 0, GENRE_OTHER_NEWS),
        Channel("KTVUDT1", "KTVU San Francisco (FOX)", 0, GENRE_OTHER_NEWS),
        Channel("WNYWDT1", "WNYW New York (FOX)", 0, GENRE_OTHER_NEWS),
        Channel("WGNDT1", "WGN Chicago (Ind)", 0, GENRE_OTHER_NEWS),
        
        // ===== Cooking (Genre 101) =====
        Channel("GordonRamsaysHellsKitchen", "Hell's Kitchen", 0, GENRE_COOKING),
        Channel("bonappetit", "Bon Appétit", 0, GENRE_COOKING),
        Channel("AmericasTestKitchen", "America's Test Kitchen", 0, GENRE_COOKING),
        Channel("Tastemade", "Tastemade", 0, GENRE_COOKING),
        
        // ===== Documentary (Genre 102) =====
        Channel("NationalGeographic", "National Geographic", 0, GENRE_DOCUMENTARY),
        Channel("NationalGeographicWild", "Nat Geo Wild", 0, GENRE_DOCUMENTARY),
        Channel("History", "History Channel", 0, GENRE_DOCUMENTARY),
        Channel("MilitaryHistory", "Military History", 0, GENRE_DOCUMENTARY),
        Channel("ForensicFiles", "Forensic Files", 0, GENRE_DOCUMENTARY),
        Channel("Dateline247", "Dateline 24/7", 0, GENRE_DOCUMENTARY),
        Channel("CourtTV", "Court TV", 0, GENRE_DOCUMENTARY),
        Channel("LawCrime", "Law & Crime", 0, GENRE_DOCUMENTARY),
        Channel("InkMaster", "Ink Master", 0, GENRE_DOCUMENTARY),
        Channel("PlutoTVHistory", "Pluto TV History", 0, GENRE_DOCUMENTARY),
        Channel("PlutoTVCrime", "Pluto TV Crime", 0, GENRE_DOCUMENTARY),
        Channel("PlutoTVInvestigation", "Pluto TV Investigation", 0, GENRE_DOCUMENTARY),
        
        // ===== Entertainment (Genre 103) =====
        Channel("AE", "A&E", 0, GENRE_ENTERTAINMENT),
        Channel("StoriesbyAMC", "AMC Stories", 0, GENRE_ENTERTAINMENT),
        Channel("BBCAmerica", "BBC America", 0, GENRE_ENTERTAINMENT),
        Channel("BETHer", "BET Her", 0, GENRE_ENTERTAINMENT),
        Channel("BETPlutoTV", "BET Pluto TV", 0, GENRE_ENTERTAINMENT),
        Channel("E", "E! Entertainment", 0, GENRE_ENTERTAINMENT),
        Channel("FX", "FX", 0, GENRE_ENTERTAINMENT),
        Channel("FXX", "FXX", 0, GENRE_ENTERTAINMENT),
        Channel("Lifetime", "Lifetime", 0, GENRE_ENTERTAINMENT),
        Channel("MTV", "MTV", 0, GENRE_ENTERTAINMENT),
        Channel("MTV2", "MTV 2", 0, GENRE_ENTERTAINMENT),
        Channel("MTVenEspanol", "MTV en Espanol", 0, GENRE_ENTERTAINMENT),
        Channel("MTVPlutoTV", "MTV Pluto TV", 0, GENRE_ENTERTAINMENT),
        Channel("MTVRidiculousness", "MTV Ridiculousness", 0, GENRE_ENTERTAINMENT),
        Channel("Oxygen", "Oxygen", 0, GENRE_ENTERTAINMENT),
        Channel("USANetwork", "USA Network", 0, GENRE_ENTERTAINMENT),
        Channel("Syfy", "Syfy", 0, GENRE_ENTERTAINMENT),
        Channel("Bravo", "Bravo", 0, GENRE_ENTERTAINMENT),
        
        // ===== Classics (Genre 104) =====
        Channel("AntennaTV", "Antenna TV", 0, GENRE_CLASSICS),
        Channel("CoziTV", "Cozi TV", 0, GENRE_CLASSICS),
        Channel("Comet", "Comet", 0, GENRE_CLASSICS),
        Channel("Grit", "Grit", 0, GENRE_CLASSICS),
        Channel("GritXtra", "Grit Xtra", 0, GENRE_CLASSICS),
        Channel("IONTV", "ION TV", 0, GENRE_CLASSICS),
        Channel("IONPlus", "ION Plus", 0, GENRE_CLASSICS),
        Channel("MeTV", "MeTV", 0, GENRE_CLASSICS),
        Channel("Reelz", "Reelz", 0, GENRE_CLASSICS),
        Channel("Buzzr", "Buzzr", 0, GENRE_CLASSICS),
        Channel("GameShowNetwork", "Game Show Network", 0, GENRE_CLASSICS),
        Channel("ThePriceIsRightTheBarkerEra", "Price Is Right (Barker)", 0, GENRE_CLASSICS),
        
        // ===== Music US (Genre 106) =====
        Channel("MTVLive", "MTV Live", 0, GENRE_MUSIC_US),
        Channel("CMT", "CMT", 0, GENRE_MUSIC_US),
        Channel("AXSTV", "AXS TV", 0, GENRE_MUSIC_US),
        Channel("VevoPop", "Vevo Pop", 0, GENRE_MUSIC_US),
        Channel("Vevo80s", "Vevo 80s", 0, GENRE_MUSIC_US),
        Channel("Vevo90s", "Vevo 90s", 0, GENRE_MUSIC_US),
        
        // ===== Movies US (Genre 105) =====
        Channel("AMC", "AMC", 0, GENRE_MOVIES_US),
        Channel("ParamountNetwork", "Paramount Network", 0, GENRE_MOVIES_US),
        Channel("MGMPlus", "MGM+", 0, GENRE_MOVIES_US),
        Channel("FXM", "FXM", 0, GENRE_MOVIES_US),
        Channel("SundanceTV", "Sundance TV", 0, GENRE_MOVIES_US),
        Channel("IFC", "IFC", 0, GENRE_MOVIES_US),
        Channel("HallmarkMoviesMysteries", "Hallmark Movies & Mysteries", 0, GENRE_MOVIES_US),
        Channel("Movies", "Movies!", 0, GENRE_MOVIES_US),
        Channel("Charge", "Charge!", 0, GENRE_MOVIES_US),
        Channel("Showtime", "Showtime", 0, GENRE_MOVIES_US),
        Channel("Starz", "Starz", 0, GENRE_MOVIES_US),
        
        // ===== Series US (Genre 107) =====
        Channel("StarTrek", "Star Trek", 0, GENRE_SERIES_US),
        Channel("TheWalkingDeadUniverse", "Walking Dead Universe", 0, GENRE_SERIES_US),
        Channel("CSI", "CSI", 0, GENRE_SERIES_US),
        Channel("DoctorWhoClassic", "Doctor Who Classic", 0, GENRE_SERIES_US),
        Channel("HellsKitchen", "Hell's Kitchen", 0, GENRE_SERIES_US),
        Channel("BarRescue", "Bar Rescue", 0, GENRE_SERIES_US),
        Channel("Cheers", "Cheers", 0, GENRE_SERIES_US),
        Channel("Frasier", "Frasier", 0, GENRE_SERIES_US),
        Channel("WalkerTexasRanger", "Walker, Texas Ranger", 0, GENRE_SERIES_US),
        Channel("UnsolvedMysteries", "Unsolved Mysteries", 0, GENRE_SERIES_US),
        Channel("Cops", "Cops", 0, GENRE_SERIES_US),
        Channel("TheBobRossChannel", "The Bob Ross Channel", 0, GENRE_SERIES_US),
        Channel("ThisOldHouse", "This Old House", 0, GENRE_SERIES_US),
        
        // ===== Sports US (Genre 108) =====
        Channel("NFLRedZone", "NFL RedZone", 0, GENRE_SPORTS_US),
        Channel("NBATV", "NBA TV", 0, GENRE_SPORTS_US),
        Channel("MLBNetwork", "MLB Network", 0, GENRE_SPORTS_US),
        Channel("NHLNetwork", "NHL Network", 0, GENRE_SPORTS_US),
        Channel("CBSSportsNetworkUSA", "CBS Sports Network", 0, GENRE_SPORTS_US),
        Channel("GolfChannel", "Golf Channel", 0, GENRE_SPORTS_US),
        Channel("TennisChannel", "Tennis Channel", 0, GENRE_SPORTS_US),
        Channel("ESPNU", "ESPN U", 0, GENRE_SPORTS_US),
        Channel("CBSSportsGolazoNetwork", "CBS Sports Golazo", 0, GENRE_SPORTS_US),
        Channel("BigTenNetwork", "Big Ten Network", 0, GENRE_SPORTS_US),
        Channel("SECNetwork", "SEC Network", 0, GENRE_SPORTS_US),
        Channel("YesNetwork", "YES Network", 0, GENRE_SPORTS_US),
        
        // ===== UK TV (Genre 109) =====
        Channel("BBCOne", "BBC One", 0, GENRE_UK),
        Channel("BBCTwo", "BBC Two", 0, GENRE_UK),
        Channel("Channel4", "Channel 4", 0, GENRE_UK),
        Channel("Channel5", "Channel 5", 0, GENRE_UK),
        Channel("ITV2", "ITV 2", 0, GENRE_UK),
        Channel("ITV3", "ITV 3", 0, GENRE_UK),
        Channel("ITV4", "ITV 4", 0, GENRE_UK),
        Channel("BBCNews", "BBC News (UK)", 0, GENRE_UK),
        Channel("GBNews", "GB News", 0, GENRE_UK),
        Channel("BBCEarth", "BBC Earth", 0, GENRE_UK),
        Channel("BBCFour", "BBC Four", 0, GENRE_UK),
        Channel("TalkTV", "TalkTV", 0, GENRE_UK),
        Channel("S4C", "S4C", 0, GENRE_UK),
        Channel("WildEarth", "WildEarth", 0, GENRE_UK),
        
        // ===== Italy (Genre 110) =====
        Channel("Rai1", "Rai 1", 0, GENRE_ITALY),
        Channel("Rai2", "Rai 2", 0, GENRE_ITALY),
        Channel("Rai3", "Rai 3", 0, GENRE_ITALY),
        Channel("RaiNews24", "Rai News 24", 0, GENRE_ITALY),
        Channel("RealTime", "Real Time", 0, GENRE_ITALY),
        Channel("DMAX", "DMAX", 0, GENRE_ITALY),
        Channel("RaiSport", "Rai Sport", 0, GENRE_ITALY),
        Channel("SportItalia", "SportItalia", 0, GENRE_ITALY),
        Channel("SuperTennis", "SuperTennis", 0, GENRE_ITALY),
        
        // ===== News (Genre 89) =====
        Channel("IranInternational", "ایران اینترنشنال", 306823, GENRE_NEWS),
        Channel("BBCPersian", "بی بی سی فارسی", 301133, GENRE_NEWS),
        Channel("VoA", "صدای آمریکا", 300480, GENRE_NEWS),
        Channel("EuroNewsFarsi", "یورونیوز فارسی", 301404, GENRE_NEWS),
        Channel("IraneFarda", "ایران فردا", 302551, GENRE_NEWS),
        Channel("AfghanistanInternational", "افغانستان اینترنشنال", 307415, GENRE_NEWS),
        Channel("IRINN", "شبکه خبر IRINN", 300486, GENRE_NEWS),
        Channel("RadioFarda", "رادیوفردا", 306419, GENRE_NEWS),
        Channel("IranOneNews", "Iran One News", 307625, GENRE_NEWS),
        
        // ===== Movies & Series (Genre 19) =====
        Channel("EkranMovies", "Ekran Movies", 306747, GENRE_MOVIES),
        Channel("Film1", "Film 1", 303523, GENRE_MOVIES),
        Channel("PersianaCinema", "پرشیانا سینما", 306892, GENRE_MOVIES),
        Channel("PersianaSeries", "Persiana Series", 307301, GENRE_MOVIES),
        Channel("PersianaComedy", "Persiana Comedy", 307313, GENRE_MOVIES),
        Channel("PersianaKorea", "Persiana Korea", 307341, GENRE_MOVIES),
        Channel("AVASeries", "AVA Series", 307252, GENRE_MOVIES),
        Channel("AVAFamily", "AVA Family", 307042, GENRE_MOVIES),
        Channel("GrandCinema", "Grand Cinema", 307227, GENRE_MOVIES),
        Channel("iFilmFarsi", "آی فیلم", 304079, GENRE_MOVIES),
        Channel("IRIBNamayesh", "نمایش", 304081, GENRE_MOVIES),
        Channel("T2Movies", "T2 Movies", 307377, GENRE_MOVIES),
        Channel("IranProudSeriesPlus", "Iran Proud Series Plus", 307357, GENRE_MOVIES),
        Channel("ClassicTV", "Classic TV", 302082, GENRE_MOVIES),
        Channel("ICC", "سینمایی ایرانیان", 302078, GENRE_MOVIES),
        
        // ===== Persian General (Genre 1) =====
        Channel("TapeshAmerica", "Tapesh America", 306606, GENRE_PERSIAN),
        Channel("ChannelOne", "کانال یک", 307514, GENRE_PERSIAN),
        Channel("ITN", "ITN آمریکا", 305049, GENRE_PERSIAN),
        Channel("ParsTV", "تلویزیون پارس", 300454, GENRE_PERSIAN),
        Channel("TAPESH", "تپش", 300447, GENRE_PERSIAN),
        Channel("MBCPersia", "MBC Persia", 300513, GENRE_PERSIAN),
        Channel("RJTV", "RJ TV", 306141, GENRE_PERSIAN),
        Channel("TasvirEIran", "تصویر ایران", 300457, GENRE_PERSIAN),
        Channel("OmidEIran", "امید ایران", 300462, GENRE_PERSIAN),
        Channel("MihanTV", "میهن", 300902, GENRE_PERSIAN),
        Channel("AyenehTV", "آینه", 300326, GENRE_PERSIAN),
        Channel("T2International", "T2 International", 307381, GENRE_PERSIAN),
        Channel("T2America", "T2 America", 307261, GENRE_PERSIAN),
        Channel("IraneAryaee", "ایران آریایی", 302136, GENRE_PERSIAN),
        Channel("SimayAzadi", "سیمای آزادی", 307555, GENRE_PERSIAN),
        Channel("YourTimeTV", "یورتایم", 307230, GENRE_PERSIAN),
        Channel("Today", "Today", 303003, GENRE_PERSIAN),
        Channel("AsreEmrooz", "عصر امروز", 301196, GENRE_PERSIAN),
        Channel("Didgah", "دیدگاه", 305207, GENRE_PERSIAN),
        Channel("Payvand", "پیوند", 305183, GENRE_PERSIAN),
        Channel("ITNEurope", "ITN ایران", 305190, GENRE_PERSIAN),
        Channel("Shabakeh7", "شبکه‌۷", 305654, GENRE_PERSIAN),
        Channel("WomanTV", "زن زندگی‌آزادی", 307008, GENRE_PERSIAN),
        Channel("PJTV", "پیام جوان", 303012, GENRE_PERSIAN),
        Channel("PersianaFamily", "پرشیانا فمیلی", 306882, GENRE_PERSIAN),
        Channel("PersianaIranian", "پرشیانا ایرانیان", 307273, GENRE_PERSIAN),
        Channel("PersianaPlus", "Persiana Plus", 307361, GENRE_PERSIAN),
        Channel("PersianaDocs", "Persiana Docs", 307478, GENRE_PERSIAN),
        Channel("PersianaReality", "Persiana Reality", 307491, GENRE_PERSIAN),
        Channel("PersianaTurkiye", "Persiana Turkiye", 307275, GENRE_PERSIAN),
        Channel("AzStarTV", "Az Star TV", 306657, GENRE_PERSIAN),
        Channel("Hambastegi", "Hambastegi", 307483, GENRE_PERSIAN),
        Channel("4UTV", "4U TV", 306743, GENRE_PERSIAN),
        Channel("4uFamily", "4u Family", 307168, GENRE_PERSIAN),
        Channel("FXTV", "FX TV", 307384, GENRE_PERSIAN),
        Channel("NetTV", "Net TV", 307305, GENRE_PERSIAN),
        Channel("MTC", "MTC", 307309, GENRE_PERSIAN),
        Channel("InfinityTV", "Infinity TV", 307335, GENRE_PERSIAN),
        Channel("MaahTV", "Maah TV", 307315, GENRE_PERSIAN),
        Channel("KhaterehTV", "خاطره", 307561, GENRE_PERSIAN),
        Channel("FarsiTV", "Farsi TV HD", 307454, GENRE_PERSIAN),
        Channel("IRTV", "IRTV ایرانیان", 307563, GENRE_PERSIAN),
        Channel("HomePlus", "Home Plus", 307565, GENRE_PERSIAN),
        Channel("DidanihaTV", "دیدنیها", 307591, GENRE_PERSIAN),
        
        // ===== IRIB (Genre 2) =====
        Channel("IRIBChannel1", "شبکه یک", 300488, GENRE_IRIB),
        Channel("IRIBChannel2", "شبکه دو", 300489, GENRE_IRIB),
        Channel("IRIBChannel3", "شبکه سه", 300490, GENRE_IRIB),
        Channel("IRIBChannel4", "شبکه چهار", 300491, GENRE_IRIB),
        Channel("IRIBChannel5", "شبکه پنج", 300492, GENRE_IRIB),
        Channel("IRIBAmoozesh", "شبکه آموزش", 301104, GENRE_IRIB),
        Channel("IRIBOfogh", "افق", 300483, GENRE_IRIB),
        Channel("IRIBVarzesh", "ورزش", 304077, GENRE_IRIB),
        Channel("IRIBNasim", "نسیم", 306206, GENRE_IRIB),
        Channel("IRIBSalamat", "سلامت", 304109, GENRE_IRIB),
        Channel("IRIBMostanad", "مستند", 304104, GENRE_IRIB),
        Channel("IRIBOmid", "شبکه امید", 306858, GENRE_IRIB),
        Channel("Tamasha", "تماشا", 305052, GENRE_IRIB),
        
        // ===== Music (Genre 10) =====
        Channel("PMCMusic", "PMC Music", 303569, GENRE_MUSIC),
        Channel("Navahang", "نواهنگ", 307617, GENRE_MUSIC),
        Channel("SunMusic", "Sun Music", 307213, GENRE_MUSIC),
        Channel("AvaFilm", "آوا فیلم", 300450, GENRE_MUSIC),
        Channel("Avang", "Avang", 307196, GENRE_MUSIC),
        Channel("SonatiMusic", "موسیقی سنتی", 302661, GENRE_MUSIC),
        Channel("Music1", "Music 1", 306898, GENRE_MUSIC),
        Channel("MeTV", "Me TV", 306959, GENRE_MUSIC),
        Channel("KMC", "KMC", 307627, GENRE_MUSIC),
        Channel("PersianaRap", "Persiana Rap", 307474, GENRE_MUSIC),
        Channel("PersianaNostalgia", "پرشیانا نوستالژی", 307299, GENRE_MUSIC),
        Channel("PersianaMusic", "پرشیانا موسیقی", 307519, GENRE_MUSIC),
        Channel("PersianaFolk", "Persiana Folk", 307353, GENRE_MUSIC),
        Channel("PMCRoyale", "PMC Royale", 307388, GENRE_MUSIC),
        Channel("Romantico", "Romantico", 307238, GENRE_MUSIC),
        Channel("AyazTV", "AyazTV", 307553, GENRE_MUSIC),
        Channel("iTVPersianMusic", "iTV Persian Music", 307343, GENRE_MUSIC),
        
        // ===== Sports (Genre 84) =====
        Channel("PersianaSports1", "Persiana Sport", 307480, GENRE_SPORTS),
        Channel("PersianaSports2", "Persiana Sports 2", 307548, GENRE_SPORTS),
        Channel("PersianaSports3", "Persiana Sports 3", 307599, GENRE_SPORTS),
        Channel("PersianaSports4", "Persiana Sports 4", 307516, GENRE_SPORTS),
        Channel("PersianaFight", "Persiana Fight", 307569, GENRE_SPORTS),
        Channel("ProSportInternational", "Pro Sport International", 307621, GENRE_SPORTS),
        Channel("GEMSPORT", "GEM Sport", 307575, GENRE_SPORTS),
        
        // ===== Kids (Genre 4) =====
        Channel("EkranKids", "Ekran Kids", 306831, GENRE_KIDS),
        Channel("PersianaJunior", "پرشیانا جونیور", 307183, GENRE_KIDS),
        Channel("IRIBPooya", "پویا", 304112, GENRE_KIDS),
        
        // ===== Religious (Genre 85) =====
        Channel("NejatTV", "Nejat TV", 307425, GENRE_RELIGIOUS),
        Channel("RaheNejat", "Rahe Nejat", 307511, GENRE_RELIGIOUS),
        Channel("ImanBeMasih", "Iman Be Masih", 307373, GENRE_RELIGIOUS),
        Channel("DerakhteZendegi", "Derakhte Zendegi", 307363, GENRE_RELIGIOUS),
        Channel("OmideJavedan", "امید جاودان", 306672, GENRE_RELIGIOUS),
        Channel("Mohabat", "محبت", 300931, GENRE_RELIGIOUS),
        Channel("KanalJadid", "Kanal Jadid", 303987, GENRE_RELIGIOUS),
        Channel("JewishTV", "Jewish TV", 307317, GENRE_RELIGIOUS),
        Channel("TahourTV", "Tahour TV", 307466, GENRE_RELIGIOUS),
        Channel("ImamHusseinTV1", "امام حسین ۱", 306954, GENRE_RELIGIOUS),
        Channel("PayameAramesh", "پیام آرامش", 303657, GENRE_RELIGIOUS),
        Channel("GanjEHozour", "گنج حضور", 301192, GENRE_RELIGIOUS),
        Channel("IsraelParsTV", "إسرائيل پارس", 307103, GENRE_RELIGIOUS)
    )
    
    // Categories for main page
    override val mainPage = mainPageOf(
        "${GENRE_OTHER_NEWS}" to "🌍 Other News",
        "${GENRE_NEWS}" to "📰 News",
        "${GENRE_MOVIES}" to "🎬 Movies & Series",
        "${GENRE_PERSIAN}" to "📺 Persian",
        "${GENRE_IRIB}" to "📡 IRIB",
        "${GENRE_MUSIC}" to "🎵 Music",
        "${GENRE_SPORTS}" to "⚽ Sports",
        "${GENRE_KIDS}" to "👶 Kids",
        "${GENRE_RELIGIOUS}" to "🕌 Religious",
        "${GENRE_COOKING}" to "🍳 Cooking",
        "${GENRE_DOCUMENTARY}" to "🦁 Documentary",
        "${GENRE_ENTERTAINMENT}" to "🎭 Entertainment",
        "${GENRE_CLASSICS}" to "🏛 Classics",
        "${GENRE_MOVIES_US}" to "🎥 Movies (US)",
        "${GENRE_MUSIC_US}" to "🎸 Music (US)",
        "${GENRE_SERIES_US}" to "📺 Series (US)",
        "${GENRE_SPORTS_US}" to "🏈 Sports (US)",
        "${GENRE_SPORTS_US}" to "🏈 Sports (US)",
        "${GENRE_UK}" to "🇬🇧 United Kingdom",
        "${GENRE_ITALY}" to "🇮🇹 Italy"
    )
    
    // Get genre name for display
    private fun getGenreName(genreId: Int): String = when(genreId) {
        GENRE_OTHER_NEWS -> "🌍 Other News"
        GENRE_NEWS -> "📰 News"
        GENRE_MOVIES -> "🎬 Movies & Series"
        GENRE_PERSIAN -> "📺 Persian"
        GENRE_IRIB -> "📡 IRIB"
        GENRE_MUSIC -> "🎵 Music"
        GENRE_SPORTS -> "⚽ Sports"
        GENRE_KIDS -> "👶 Kids"
        GENRE_RELIGIOUS -> "🕌 Religious"
        GENRE_COOKING -> "🍳 Cooking"
        GENRE_DOCUMENTARY -> "🦁 Documentary"
        GENRE_ENTERTAINMENT -> "🎭 Entertainment"
        GENRE_CLASSICS -> "🏛 Classics"
        GENRE_MOVIES_US -> "🎥 Movies (US)"
        GENRE_MUSIC_US -> "🎸 Music (US)"
        GENRE_SERIES_US -> "📺 Series (US)"
        GENRE_SPORTS_US -> "🏈 Sports (US)"
        GENRE_UK -> "🇬🇧 United Kingdom"
        GENRE_ITALY -> "🇮🇹 Italy"
        else -> "📺 Other"
    }
    
    private fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept", "*/*")
        
        headers.forEach { (key, value) -> requestBuilder.header(key, value) }
        val response = httpClient.newCall(requestBuilder.build()).execute()
        return response.body?.string() ?: ""
    }
    
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(request.name, emptyList())
        val genreId = request.data.toIntOrNull() ?: GENRE_NEWS
        val filtered = allChannels.filter { it.genreId == genreId }.distinctBy { it.streamName }
        
        val home = filtered.map { channel ->
            newMovieSearchResponse(
                channel.displayName,
                "$mainUrl/ch/${channel.streamName}|${channel.genreId}",
                TvType.Live
            ) {
                this.posterUrl = channel.logoUrl
            }
        }
        
        return newHomePageResponse(request.name, home)
    }
    
    override suspend fun search(query: String): List<SearchResponse> {
        val results = allChannels.filter { 
            it.displayName.contains(query, ignoreCase = true) ||
            it.streamName.contains(query, ignoreCase = true)
        }.distinctBy { it.streamName }
        
        return results.map { channel ->
            newMovieSearchResponse(
                channel.displayName,
                "$mainUrl/ch/${channel.streamName}|${channel.genreId}",
                TvType.Live
            ) {
                this.posterUrl = channel.logoUrl
            }
        }
    }
    
    override suspend fun load(url: String): LoadResponse {
        // URL format: mainUrl/ch/StreamName|GenreId
        val parts = url.substringAfterLast("/ch/").split("|")
        val streamName = parts.getOrNull(0) ?: ""
        val genreId = parts.getOrNull(1)?.toIntOrNull() ?: GENRE_PERSIAN
        
        val channel = allChannels.find { it.streamName == streamName }
        
        // Get related channels from same genre (excluding current)
        val relatedChannels = allChannels
            .filter { it.genreId == genreId && it.streamName != streamName }
            .shuffled()
            .take(12)
        
        val recommendations = relatedChannels.map { ch ->
            newMovieSearchResponse(
                ch.displayName,
                "$mainUrl/ch/${ch.streamName}|${ch.genreId}",
                TvType.Live
            ) {
                this.posterUrl = ch.logoUrl
            }
        }
        
        return newMovieLoadResponse(
            channel?.displayName ?: streamName,
            url,
            TvType.Live,
            streamName
        ) {
            this.posterUrl = channel?.logoUrl ?: ""
            this.plot = "📺 ${channel?.displayName ?: streamName}\n\n${getGenreName(genreId)}"
            this.recommendations = recommendations
        }
    }
    
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val streamName = data
            var foundAny = false
            
            // 1. EXTRA SOURCES (Direct/YouTube/Scraped)
            if (otherNewsUrls.containsKey(streamName)) {
                val urls = otherNewsUrls[streamName]!!
                
                urls.forEach { url ->
                    try {
                        // Handle YouTube links
                        if (url.contains("youtube.com") || url.contains("youtu.be")) {
                            loadExtractor(url, mainUrl, subtitleCallback, callback)
                            foundAny = true
                            return@forEach
                        }

                        // Handle direct m3u8 links (user provided)
                        if (url.endsWith(".m3u8")) {
                             callback.invoke(
                                newExtractorLink(
                                    source = name,
                                    name = "$name - $streamName (Direct)",
                                    url = url
                                ).apply {
                                    this.referer = "https://iptv-web.app/"
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                            foundAny = true
                            return@forEach
                        }
                        
                        // We use standard app.get() for these sites
                        val response = app.get(url).text
                        // Find .m3u8 link (it works for both static and dynamic with tokens)
                        // 1. Find standard .m3u8 links (always safe to grab)
                        val m3u8Regex = Regex("""https?://[^"']+\.m3u8[^"']*""")
                        val m3u8Matches = m3u8Regex.findAll(response).map { 
                            it.value.substringBefore("&quot;")
                                .replace("\\/", "/")
                                .replace("&amp;", "&")
                        }

                        // 2. Find links hidden in Astro/JSON props - Broadened to catch any URL
                        // Pattern: [0,&quot;URL&quot;]
                        val jsonRegex = Regex("""\[0,&quot;(https?://.*?)&quot;\]""")
                        val jsonMatches = jsonRegex.findAll(response).map {
                             it.groupValues[1]
                                .replace("\\/", "/")
                                .replace("&amp;", "&")
                        }
                        
                        // 3. Generic Player Config Extraction (src: '...', source: '...', file: '...')
                        val playerScriptRegex = Regex("""(?:src|source|file)\s*:\s*['"](https?://[^'"]+)['"]""")
                        val scriptMatches = playerScriptRegex.findAll(response).map {
                            it.groupValues[1].replace("\\/", "/")
                        }

                        // 4. Jsoup Extraction for Iframes and Video Sources
                        val doc = Jsoup.parse(response)
                        val iframeSrcs = doc.select("iframe[src]").map { it.attr("src") }
                            .filter { it.startsWith("http") && !it.contains("doubleclick") && !it.contains("google") } // Basic ad filter
                        val videoSrcs = doc.select("video source[src]").map { it.attr("src") }

                        // Combine all found potential stream URLs
                        val allMatches = (m3u8Matches + jsonMatches + scriptMatches + iframeSrcs + videoSrcs)
                            .map { it.trim() }
                            .distinct()
                        
                        allMatches.forEach { streamUrl ->
                            // Basic filter to avoid junk (e.g. empty strings, non-urls)
                            if (streamUrl.length > 10 && streamUrl.startsWith("http")) {
                                callback.invoke(
                                    newExtractorLink(
                                        source = name,
                                        name = "$name - $streamName (${if(streamUrl.contains(".m3u8")) "HLS" else "Stream"})",
                                        url = streamUrl
                                    ).apply {
                                        this.referer = if (url.contains("iptv-web")) "https://iptv-web.app/" else "https://www.newslive.com/"
                                        this.quality = Qualities.Unknown.value
                                    }
                                )
                                foundAny = true
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // 2. STANDARD GLWIZ LOGIC (If channel has an ID)
            val channel = allChannels.find { it.streamName == streamName }
            if (channel != null && channel.id > 0) {
                try {
                    cookieStore.clear()
                    httpGet("$playerBaseUrl/Player.aspx")
                    
                    val requestUrl = "$ajaxUrl?action=getStreamURL&ClusterName=zixi-glwiz-mobile&RecType=4&itemName=$streamName&ScreenMode=0"
                    val response = httpGet(requestUrl, mapOf(
                        "Referer" to "$playerBaseUrl/p2.html",
                        "X-Requested-With" to "XMLHttpRequest"
                    ))
                    
                    val respRegex = Regex(""""resp"\s*:\s*"([^"]+)"""")
                    val match = respRegex.find(response)
                    
                    if (match != null) {
                        val streamUrl = match.groupValues[1]
                            .replace("\\u0026", "&")
                            .replace("\\/", "/")
                            // Fix unencoded spaces which break playback
                            .replace(" ", "%20")
                        
                        if (!streamUrl.contains("GlwizPromo")) {
                            callback.invoke(
                                newExtractorLink(
                                    source = name,
                                    name = "$name - $streamName (GLWiz)",
                                    url = streamUrl
                                ).apply {
                                    this.quality = Qualities.Unknown.value
                                    this.referer = "$playerBaseUrl/p2.html"
                                    this.headers = mapOf(
                                        "Referer" to "$playerBaseUrl/p2.html",
                                        "Origin" to mainUrl
                                    )
                                }
                            )
                            foundAny = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            return foundAny
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
