package app.gamenative.provider

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class AllDebridMagnetFilesTest {
    @Test
    fun `flattens nested magnet folders into downloadable files`() {
        val nodes = JSONArray(
            """
            [
              {"n":"Game","e":[
                {"n":"part1.rar","s":10,"l":"https://alldebrid.com/f/one"},
                {"n":"Extra","e":[{"n":"part2.rar","s":20,"l":"https://alldebrid.com/f/two"}]}
              ]},
              {"n":"readme.txt","s":4,"l":"https://alldebrid.com/f/readme"}
            ]
            """.trimIndent(),
        )
        val files = AllDebridMagnetFiles.flatten(nodes)
        assertEquals(3, files.size)
        assertEquals("Game/part1.rar", files[0].relativePath)
        assertEquals("Game/Extra/part2.rar", files[1].relativePath)
        assertEquals("readme.txt", files[2].relativePath)
        assertEquals(20L, files[1].sizeBytes)
    }
}
