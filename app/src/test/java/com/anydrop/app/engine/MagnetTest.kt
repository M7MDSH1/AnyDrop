package com.anydrop.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetTest {

    // WebTorrent's free sample torrent (Big Buck Bunny, CC BY).
    private val bunny = "magnet:?xt=urn:btih:DD8255ECDC7CA55FB0BBF81323D87062DB1F6D1C&dn=Big+Buck+Bunny" +
        "&tr=udp%3A%2F%2Fexplodie.org%3A6969&tr=wss%3A%2F%2Ftracker.webtorrent.dev"

    @Test
    fun infoHash_fromHexOrBase32() {
        assertEquals("dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c", Magnet.infoHash(bunny))
        assertEquals(
            "dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c",
            Magnet.infoHash("magnet:?xt=urn:btih:3WBFL3G4PSSV7MF37AJSHWDQMLNR63I4")
        )
        assertNull(Magnet.infoHash("magnet:?dn=no-hash"))
        assertNull(Magnet.infoHash("magnet:?xt=urn:btih:not-a-hash"))
    }

    @Test
    fun displayName_isDecoded() {
        assertEquals("Big Buck Bunny", Magnet.displayName(bunny))
        assertNull(Magnet.displayName("magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c"))
    }

    @Test
    fun trackersAndWebSeeds_areRead() {
        assertEquals(listOf("udp://explodie.org:6969", "wss://tracker.webtorrent.dev"), Magnet.trackers(bunny))
        val withSeed = "$bunny&ws=https%3A%2F%2Fwebtorrent.io%2Ftorrents%2F&ws=ftp%3A%2F%2Fnope"
        assertEquals(listOf("https://webtorrent.io/torrents/"), Magnet.webSeeds(withSeed))
        assertTrue(Magnet.trackers("magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c").isEmpty())
    }

    @Test
    fun linksAreFoundAndRecognised() {
        assertEquals(bunny, Magnet.find("Watch this: $bunny (free film)"))
        assertTrue(Magnet.isTorrentLink(bunny))
        assertTrue(Magnet.isTorrentLink("https://webtorrent.io/torrents/sintel.torrent"))
        assertFalse(Magnet.isTorrentLink("https://www.youtube.com/watch?v=aqz-KE-bpKQ"))
    }

    @Test
    fun aTorrentFilesMagnet_readsBack() {
        val link = Magnet.of("08ada5a7a6183aae1e09d831df6748d566095a10", "Sintel (2010)")
        assertEquals("08ada5a7a6183aae1e09d831df6748d566095a10", Magnet.infoHash(link))
        assertEquals("Sintel (2010)", Magnet.displayName(link))
    }

    private val film = TorrentMeta(
        infoHash = "x",
        name = "Sintel",
        totalSize = 0,
        files = listOf(
            TorrentFile(0, "Sintel/Sintel.mp4", 129_000_000),
            TorrentFile(1, "Sintel/sample.mkv", 4_000_000),
            TorrentFile(2, "Sintel/poster.jpg", 200_000),
            TorrentFile(3, "Sintel/Sintel.en.srt", 30_000),
            TorrentFile(4, "Sintel/info.nfo", 1_000)
        )
    )

    @Test
    fun theFilmIsTicked_notTheSampleOrExtras() {
        assertEquals(setOf(0), film.defaultSelection())
        // Subtitles and text aren't offered; the poster is.
        assertEquals(listOf(0, 1, 2), film.mediaFiles.map { it.index })
    }

    @Test
    fun anAlbumTicksItsSongs() {
        val album = TorrentMeta(
            "y", "Album", 0,
            listOf(TorrentFile(0, "A/01.flac", 30_000_000), TorrentFile(1, "A/02.flac", 25_000_000), TorrentFile(2, "A/cover.jpg", 500_000))
        )
        assertEquals(setOf(0, 1), album.defaultSelection())
    }

    @Test
    fun torrentFiles_travelThroughTheDeckList() {
        val formats = film.files.map { it.toFormat("Sintel") }
        assertTrue(formats.all { it.isTorrent })
        assertEquals(film.files, formats.torrentFileList())
        val download = listOf(film.files[0], film.files[2]).asDownload("Sintel")
        assertEquals(setOf(0, 2), download.torrentFiles)
        assertEquals("mp4", download.extension)
        assertEquals("129 MB", download.resolution)
    }
}
