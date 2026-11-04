package com.shamela.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Walks the app's everyday paths so their code lands in the Baseline Profile: start, every tab of
 * the navigation bar, the in-page tabs, opening a section and a book's details, and going back.
 * Labels are matched in Turkish and Arabic, whichever language the app is in.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.meminglr.shamela") {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        tab("İndir|التحميل")
        // In-page tabs, there and back.
        click("Kitaplar|الكتب")
        click("Bölümler|الأقسام")
        // A section of the catalog, a book's details, and back out of both.
        if (click("التفاسير")) {
            device.wait(Until.hasObject(By.textContains("Kitap sayısı").pkg(packageName)), 5_000)
            device.findObjects(By.clickable(true).pkg(packageName)).getOrNull(4)?.click()
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()
        }
        tab("Kaydedilenler|المحفوظات")
        tab("Kütüphane|المكتبة")
        click("Kitaplar|الكتب")
        click("Bölümler|الأقسام")
        tab("İndir|التحميل")
        tab("Kütüphane|المكتبة")
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.tab(labels: String) = click(labels)

    /** Clicks the first element whose text matches [labels] (a regex), if there is one. */
    private fun androidx.benchmark.macro.MacrobenchmarkScope.click(labels: String): Boolean {
        val target = device.wait(Until.findObject(By.text(Pattern.compile(labels)).pkg(packageName)), 5_000)
            ?: return false
        target.click()
        device.waitForIdle()
        // Let the page transition finish.
        Thread.sleep(800)
        return true
    }

}
