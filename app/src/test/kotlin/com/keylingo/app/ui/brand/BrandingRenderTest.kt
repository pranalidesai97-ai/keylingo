package com.keylingo.app.ui.brand

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import com.keylingo.app.R
import com.keylingo.app.ui.theme.KeyLingoTheme
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

/**
 * The launcher icon and in-app mark: resources inflate (adaptive icon with a
 * monochrome layer for themed icons), and previews are rendered to
 * build/keyboard-screenshots/brand-*.png in common launcher shapes and sizes.
 * JVM (Robolectric) renders for review, not device screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class BrandingRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(listOf(File("build"), File("app/build")).first { it.isDirectory }, "keyboard-screenshots").apply { mkdirs() }

    private fun save(bitmap: Bitmap, name: String) =
        File(outDir, "brand-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    /** Draws background + foreground (108dp canvas; the visible icon is the central 72dp) masked to [shape]. */
    private fun renderIcon(icon: AdaptiveIconDrawable, sizePx: Int, shape: (Float) -> Path, monochromeOn: Int? = null): Bitmap {
        val full = (sizePx * 108f / 72f).toInt()
        val offset = (full - sizePx) / 2
        val layers = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        Canvas(layers).apply {
            translate(-offset.toFloat(), -offset.toFloat())
            if (monochromeOn != null) {
                drawColor(monochromeOn)
                icon.monochrome!!.mutate().apply { setBounds(0, 0, full, full); colorFilter = PorterDuffColorFilter(0xFF1F3A5F.toInt(), PorterDuff.Mode.SRC_IN) }.draw(this)
            } else {
                icon.background.setBounds(0, 0, full, full); icon.background.draw(this)
                icon.foreground.setBounds(0, 0, full, full); icon.foreground.draw(this)
            }
        }
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            drawPath(shape(sizePx.toFloat()), paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            drawBitmap(layers, 0f, 0f, paint)
        }
        return out
    }

    private val circle = { s: Float -> Path().apply { addCircle(s / 2, s / 2, s / 2, Path.Direction.CW) } }
    private val roundedSquare = { s: Float -> Path().apply { addRoundRect(RectF(0f, 0f, s, s), s * 0.22f, s * 0.22f, Path.Direction.CW) } }
    private val squircle = { s: Float ->
        Path().apply {
            val r = s / 2; val n = 4.0
            for (i in 0..360) {
                val t = Math.toRadians(i.toDouble())
                val x = r + r * abs(kotlin.math.cos(t)).pow(2 / n) * sign(kotlin.math.cos(t))
                val y = r + r * abs(kotlin.math.sin(t)).pow(2 / n) * sign(kotlin.math.sin(t))
                if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
            }
            close()
        }
    }
    private val teardrop = { s: Float ->
        Path().apply {
            addRoundRect(RectF(0f, 0f, s, s), floatArrayOf(s / 2, s / 2, s / 2, s / 2, s * 0.12f, s * 0.12f, s / 2, s / 2), Path.Direction.CW)
        }
    }

    @Test fun launcherIconIsAnAdaptiveIconWithAThemedLayer() {
        val icon = compose.activity.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val round = compose.activity.getDrawable(R.mipmap.ic_launcher_round) as AdaptiveIconDrawable
        assertNotNull(icon.background); assertNotNull(icon.foreground); assertNotNull(icon.monochrome); assertNotNull(round.monochrome)
        assertNotNull(compose.activity.getDrawable(R.drawable.ic_keylingo_mark))

        // Sheet: four launcher shapes at 192px, small sizes (48/36px), and a themed (monochrome) icon.
        val shapes = listOf(circle, squircle, roundedSquare, teardrop)
        val sheet = Bitmap.createBitmap(4 * 216 + 24, 216 + 120 + 24, Bitmap.Config.ARGB_8888)
        Canvas(sheet).apply {
            drawColor(Color.WHITE)
            shapes.forEachIndexed { i, shape -> drawBitmap(renderIcon(icon, 192, shape), 24f + i * 216, 24f, null) }
            var x = 24f
            for (size in listOf(96, 72, 48, 36)) { drawBitmap(renderIcon(icon, size, circle), x, 240f + (96 - size) / 2f, null); x += size + 24 }
            drawBitmap(renderIcon(icon, 96, circle, monochromeOn = 0xFFD8E6F5.toInt()), x + 24, 240f, null)
            drawBitmap(renderIcon(icon, 96, squircle, monochromeOn = 0xFFE9DDF2.toInt()), x + 144, 240f, null)
        }
        save(sheet, "launcher-icon-sheet")
        // Dark wallpaper check.
        val dark = Bitmap.createBitmap(3 * 216 + 24, 240, Bitmap.Config.ARGB_8888)
        Canvas(dark).apply {
            drawColor(0xFF15171C.toInt())
            listOf(circle, squircle, roundedSquare).forEachIndexed { i, shape -> drawBitmap(renderIcon(icon, 192, shape), 24f + i * 216, 24f, null) }
        }
        save(dark, "launcher-icon-dark-wallpaper")

        // The mark is legible at small sizes: the white front bubble and the dark K both survive at 48px.
        val small = renderIcon(icon, 48, circle)
        var white = 0; var deep = 0
        for (yy in 0 until 48) for (xx in 0 until 48) {
            val p = small.getPixel(xx, yy)
            if (Color.red(p) > 235 && Color.green(p) > 235 && Color.blue(p) > 235) white++
            if (Color.blue(p) > 80 && Color.red(p) < 60 && Color.green(p) < 90) deep++
        }
        assertTrue("front bubble visible at 48px ($white px)", white > 250)
        assertTrue("deep brand tones visible at 48px ($deep px)", deep > 40)
    }

    @Test fun wordmarkRendersOnLightAndDark() {
        compose.setContent {
            androidx.compose.foundation.layout.Column {
                KeyLingoTheme(darkTheme = false) { Surface { Box(Modifier.padding(24.dp)) { KeyLingoWordmark(markSize = 56.dp) } } }
                KeyLingoTheme(darkTheme = true) { Surface { Box(Modifier.padding(24.dp)) { KeyLingoWordmark(markSize = 56.dp) } } }
                KeyLingoTheme(darkTheme = false) { Surface { Box(Modifier.padding(24.dp)) { KeyLingoWordmark(markSize = 32.dp) } } }
            }
        }
        compose.waitForIdle()
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("KeyLingo")).fetchSemanticsNodes().let { assertTrue(it.size == 3) }
        val root = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
        save(Bitmap.createBitmap(bmp, 0, 0, bmp.width, minOf(bmp.height, (3 * 104 * 3) + 60)), "wordmark")
    }

    @Test fun setupScreenShowsTheWordmark() {
        compose.setContent { KeyLingoTheme(darkTheme = false) { com.keylingo.app.ui.onboarding.OnboardingScreen(onOpenSettings = {}) } }
        compose.onNodeWithContentDescription("KeyLingo").assertIsDisplayed()
        compose.waitForIdle()
        val root = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
        save(bmp, "setup-screen")
    }
}
