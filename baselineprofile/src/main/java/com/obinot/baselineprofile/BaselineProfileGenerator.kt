package com.obinot.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.obinot.app"
    ) {
        // 1. Arranque en frío: la app abre en RecordScreen
        pressHome()
        startActivityAndWait()

        // 2. Esperar a que cargue la pantalla de grabación
        device.wait(Until.hasObject(By.text("Grabar")), 5000)

        // 3. Navegar a History
        device.findObject(By.text("Historial"))?.click()
        device.wait(Until.hasObject(By.text("Buscar notas…")), 3000)

        // 4. Navegar a Settings
        device.findObject(By.text("Ajustes"))?.click()
        device.wait(Until.hasObject(By.text("Personalización")), 3000)

        // 5. Volver a Record
        device.findObject(By.text("Grabar"))?.click()
        device.wait(Until.hasObject(By.text("Grabar")), 3000)
    }
}