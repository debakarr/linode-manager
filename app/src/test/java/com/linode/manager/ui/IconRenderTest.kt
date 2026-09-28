package com.linode.manager.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.linode.manager.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the launcher icon the way launchers mask it (circle, squircle,
 * rounded square, teardrop) plus the Android 13 themed version, into
 * build/icon/. Fails if any layer can't be inflated.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "xxxhdpi")
class IconRenderTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val size = 432 // 108dp * 4 (xxxhdpi)

    private fun layer(
        id: Int,
        tint: Int? = null,
    ): Bitmap {
        val d = ContextCompat.getDrawable(ctx, id)!!.mutate()
        if (tint != null) d.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(b))
        return b
    }

    private fun masked(
        mask: Path,
        vararg layers: Bitmap,
    ): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.clipPath(mask)
        layers.forEach { c.drawBitmap(it, 0f, 0f, null) }
        return out
    }

    private fun save(
        b: Bitmap,
        name: String,
    ) {
        val f = File("build/icon/$name.png")
        f.parentFile!!.mkdirs()
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderLauncherIcon() {
        val bg = layer(R.drawable.ic_launcher_background)
        val fg = layer(R.drawable.ic_launcher_foreground)
        val s = size.toFloat()
        val inset = s * 18f / 108f // launchers show the middle 72dp
        val box = RectF(inset, inset, s - inset, s - inset)
        val r = box.width() / 2

        val masks =
            linkedMapOf(
                "circle" to Path().apply { addCircle(s / 2, s / 2, r, Path.Direction.CW) },
                "squircle" to Path().apply { addRoundRect(box, r * 0.72f, r * 0.72f, Path.Direction.CW) },
                "rounded-square" to Path().apply { addRoundRect(box, r * 0.35f, r * 0.35f, Path.Direction.CW) },
                "teardrop" to
                    Path().apply {
                        addRoundRect(box, floatArrayOf(r, r, r, r, r * 0.3f, r * 0.3f, r, r), Path.Direction.CW)
                    },
            )
        masks.forEach { (name, m) -> save(masked(m, bg, fg), "icon-$name") }

        // Themed icon: launcher tints the monochrome layer on a pale background.
        val mono = layer(R.drawable.ic_launcher_monochrome, tint = Color.parseColor("#1B3A2B"))
        val pale = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.parseColor("#D5EDE0")) }
        save(masked(masks.getValue("circle"), pale, mono), "icon-themed")

        // Contact sheet of all variants on a neutral background.
        val sheet = Bitmap.createBitmap(size * 5, size, Bitmap.Config.ARGB_8888)
        val sc = Canvas(sheet)
        sc.drawColor(Color.parseColor("#EEF1F4"))
        (masks.keys.map { "icon-$it" } + "icon-themed").forEachIndexed { i, n ->
            val b = android.graphics.BitmapFactory.decodeFile("build/icon/$n.png")
            sc.drawBitmap(b, (i * size).toFloat(), 0f, Paint(Paint.ANTI_ALIAS_FLAG))
        }
        save(sheet, "icon-sheet")

        // Full-bleed 512px store/README icon (rounded square).
        val big = Bitmap.createScaledBitmap(masked(masks.getValue("rounded-square"), bg, fg), 512, 512, true)
        save(Bitmap.createBitmap(big, (512 * 18 / 108), (512 * 18 / 108), 512 * 72 / 108, 512 * 72 / 108), "icon-readme")
    }
}
