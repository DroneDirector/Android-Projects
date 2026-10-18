package com.dronedirector.veyrocutpro.editor

/**
 * Timeline exchange with desktop editors (Deliver → SEND TO DESKTOP EDITOR):
 *  - CMX 3600 EDL: cuts, dissolves and speed changes; opens in DaVinci Resolve, Premiere Pro, Avid.
 *  - FCPXML 1.9: cuts and markers; opens in DaVinci Resolve and Final Cut Pro (Premiere via a converter).
 * Only the edit travels: grades, effects and layers stay in VeyroCut Pro. Media is matched by file name,
 * so on the computer you point the editor at the folder with the original clips.
 */
object Interchange {
    private fun frames(ms: Long, fps: Int) = Math.round(ms * fps / 1000.0)

    private fun tc(frames: Long, fps: Int): String {
        val f = frames.coerceAtLeast(0)
        val ff = f % fps; val s = f / fps
        return "%02d:%02d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60, ff)
    }

    private fun reel(name: String) = name.substringBeforeLast('.').uppercase().replace(Regex("[^A-Z0-9]"), "").take(8).ifEmpty { "AX" }

    fun edl(title: String, clips: List<Clip>, fps: Int): String {
        val sb = StringBuilder()
        sb.append("TITLE: ").append(title.take(60)).append("\nFCM: NON-DROP FRAME\n\n")
        val starts = clipStarts(clips)
        var event = 1
        clips.forEachIndexed { i, c ->
            val recIn = frames(starts[i], fps)
            val recOut = recIn + frames(c.lengthMs, fps)
            val srcIn = frames(c.startMs, fps)
            val srcOut = srcIn + (recOut - recIn)
            val r = reel(c.name)
            val inOv = if (i > 0) overlapMs(clips, i - 1) else 0L
            if (inOv > 0) {
                // Dissolve from the previous clip into this one.
                val d = frames(inOv, fps)
                val prev = clips[i - 1]
                val pSrcOut = frames(prev.endMs - (inOv * prev.speed).toLong(), fps)
                sb.append("%03d  %-8s V     C        %s %s %s %s\n".format(event, reel(prev.name), tc(pSrcOut, fps), tc(pSrcOut, fps), tc(recIn, fps), tc(recIn, fps)))
                sb.append("%03d  %-8s V     D    %03d %s %s %s %s\n".format(event, r, d, tc(srcIn, fps), tc(srcOut, fps), tc(recIn, fps), tc(recOut, fps)))
            } else {
                sb.append("%03d  %-8s AA/V  C        %s %s %s %s\n".format(event, r, tc(srcIn, fps), tc(srcOut, fps), tc(recIn, fps), tc(recOut, fps)))
            }
            if (c.speed != 1f) sb.append("M2   %-8s %05.1f                %s\n".format(r, c.speed * fps, tc(srcIn, fps)))
            sb.append("* FROM CLIP NAME: ").append(c.name).append('\n')
            if (c.ramp != null) sb.append("* SPEED RAMP IN VEYROCUT PRO (NOT TRANSFERRED)\n")
            sb.append('\n')
            event++
        }
        return sb.toString()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun t(frames: Long, fps: Int) = "${frames * 100}/${fps * 100}s"

    fun fcpxml(title: String, clips: List<Clip>, fps: Int, width: Int, height: Int, markers: List<Long>, notes: Map<Long, String> = emptyMap()): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE fcpxml>\n<fcpxml version=\"1.9\">\n  <resources>\n")
        sb.append("    <format id=\"r1\" frameDuration=\"100/${fps * 100}s\" width=\"$width\" height=\"$height\"/>\n")
        val assets = LinkedHashMap<String, Pair<String, Clip>>()
        for (c in clips) if (c.name !in assets) assets[c.name] = "a${assets.size + 1}" to c
        for ((name, v) in assets) {
            val (id, c) = v
            sb.append("    <asset id=\"$id\" name=\"${esc(name)}\" start=\"0s\" duration=\"${t(frames(c.durationMs, fps), fps)}\" hasVideo=\"1\" hasAudio=\"1\" format=\"r1\">\n")
            sb.append("      <media-rep kind=\"original-media\" src=\"file:///VeyroCutPro/${esc(name)}\"/>\n    </asset>\n")
        }
        val total = frames(clips.sumOf { it.lengthMs }, fps)
        sb.append("  </resources>\n  <library>\n    <event name=\"VeyroCut Pro\">\n      <project name=\"${esc(title)}\">\n")
        sb.append("        <sequence format=\"r1\" duration=\"${t(total, fps)}\" tcStart=\"0s\" tcFormat=\"NDF\">\n          <spine>\n")
        var off = 0L
        var ms = 0L
        for (c in clips) {
            val len = frames(c.lengthMs, fps)
            val id = assets[c.name]!!.first
            sb.append("            <asset-clip ref=\"$id\" offset=\"${t(off, fps)}\" name=\"${esc(c.name)}\" start=\"${t(frames(c.startMs, fps), fps)}\" duration=\"${t(len, fps)}\" format=\"r1\">\n")
            if (c.speed != 1f && c.ramp == null) {
                sb.append("              <timeMap><timept time=\"0s\" value=\"0s\" interp=\"linear\"/><timept time=\"${t(len, fps)}\" value=\"${t(Math.round(len * c.speed.toDouble()), fps)}\" interp=\"linear\"/></timeMap>\n")
            }
            for (m in markers) if (m >= ms && m < ms + c.lengthMs) {
                val at = frames(c.startMs, fps) + frames(m - ms, fps)
                sb.append("              <marker start=\"${t(at, fps)}\" duration=\"100/${fps * 100}s\" value=\"${esc(notes[m] ?: "Marker")}\"/>\n")
            }
            sb.append("            </asset-clip>\n")
            off += len; ms += c.lengthMs
        }
        sb.append("          </spine>\n        </sequence>\n      </project>\n    </event>\n  </library>\n</fcpxml>\n")
        return sb.toString()
    }

    /** Subtitles (.srt) from the caption layers, in timeline order. */
    fun srt(layers: List<Layer>): String {
        fun ts(ms: Long): String { val m = ms.coerceAtLeast(0); return "%02d:%02d:%02d,%03d".format(m / 3_600_000, (m / 60_000) % 60, (m / 1000) % 60, m % 1000) }
        val caps = layers.filter { it.kind == LayerKind.TEXT && it.name == "Caption" && !it.wholeVideo && it.text.isNotBlank() }.sortedBy { it.startMs }
        val sb = StringBuilder()
        caps.forEachIndexed { i, l -> sb.append(i + 1).append('\n').append(ts(l.startMs)).append(" --> ").append(ts(l.endMs)).append('\n').append(l.text.trim()).append("\n\n") }
        return sb.toString()
    }
}
