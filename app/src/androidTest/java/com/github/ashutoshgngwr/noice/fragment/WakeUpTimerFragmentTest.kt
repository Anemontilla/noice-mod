package com.github.ashutoshgngwr.noice.fragment

import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.PickerActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.EspressoX
import com.github.ashutoshgngwr.noice.InAppReviewFlowManager
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.RetryTestRule
import com.github.ashutoshgngwr.noice.WakeUpTimerManager
import com.github.ashutoshgngwr.noice.sound.Preset
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.verify
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class WakeUpTimerFragmentTest {

  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  private lateinit var fragmentScenario: FragmentScenario<WakeUpTimerFragment>

  @Before
  fun setup() {
    mockkObject(InAppReviewFlowManager)
    mockkObject(Preset.Companion)
    mockkObject(WakeUpTimerManager)
    every { WakeUpTimerManager.set(any(), any()) } returns Unit
    fragmentScenario = launchFragmentInContainer<WakeUpTimerFragment>(null, R.style.Theme_App)
  }

  @Test
  fun testInitialLayout_whenTimerIsNotPreScheduled() {
    onView(withId(R.id.select_preset_button))
      .check(matches(isEnabled()))
      .check(matches(withText(R.string.select_preset)))

    onView(withId(R.id.set_time_button))
      .check(matches(not(isEnabled())))

    onView(withId(R.id.reset_time_button))
      .check(matches(not(isEnabled())))
  }

  @Test
  fun testInitialLayout_whenTimerIsPreScheduled() {
    every { Preset.findByName(any(), "test") } returns mockk()
    every { WakeUpTimerManager.get(any()) } returns mockk {
      every { presetName } returns "test"
      every { atMillis } returns System.currentTimeMillis() + 10000L
    }

    fragmentScenario.recreate()
    onView(withId(R.id.select_preset_button))
      .check(matches(isEnabled()))
      .check(matches(withText("test")))

    onView(withId(R.id.set_time_button))
      .check(matches(isEnabled()))

    onView(withId(R.id.reset_time_button))
      .check(matches(isEnabled()))
  }

  @Test
  fun testInitialLayout_whenTimerIsPreScheduled_andPresetIsDeletedFromStorage() {
    every { Preset.findByName(any(), "test") } returns null
    every { WakeUpTimerManager.get(any()) } returns mockk {
      every { presetName } returns "test"
      every { atMillis } returns System.currentTimeMillis() + 10000L
    }

    fragmentScenario.recreate()
    onView(withId(R.id.select_preset_button))
      .check(matches(isEnabled()))
      .check(matches(withText(R.string.select_preset)))

    onView(withId(R.id.set_time_button))
      .check(matches(not(isEnabled())))

    onView(withId(R.id.reset_time_button))
      .check(matches(not(isEnabled())))
  }

  @Test
  fun testSelectPreset_withoutSavedPresets() {
    every { Preset.readAllFromUserPreferences(any()) } returns arrayOf()
    onView(withId(R.id.select_preset_button))
      .check(matches(withText(R.string.select_preset)))
      .perform(click())

    EspressoX.waitForView(withText(R.string.preset_info__description), 100, 5)
      .check(matches(isDisplayed()))
  }

  @Test
  fun testSetTimer() {
    every { Preset.readAllFromUserPreferences(any()) } returns arrayOf(
      mockk(relaxed = true) { every { name } returns "test-1" },
      mockk(relaxed = true) { every { name } returns "test-2" }
    )

    onView(withId(R.id.select_preset_button))
      .check(matches(withText(R.string.select_preset)))
      .perform(click())

    EspressoX.waitForView(withId(android.R.id.list), 100, 5)
      .check(matches(withChild(withText("test-1"))))
      .check(matches(withChild(withText("test-2"))))

    onView(withText("test-1"))
      .perform(click())

    onView(withId(R.id.select_preset_button))
      .check(matches(withText("test-1")))

    verify(exactly = 0) { WakeUpTimerManager.set(any(), any()) }

    onView(withId(R.id.time_picker))
      .perform(PickerActions.setTime(1, 2))

    onView(withId(R.id.set_time_button))
      .check(matches(isEnabled()))
      .perform(scrollTo(), click())

    val calendar = Calendar.getInstance()
    val timerSlot = slot<WakeUpTimerManager.Timer>()
    verify(exactly = 1) {
      WakeUpTimerManager.set(any(), capture(timerSlot))
      InAppReviewFlowManager.maybeAskForReview(any())
    }

    calendar.timeInMillis = timerSlot.captured.atMillis
    assertEquals("test-1", timerSlot.captured.presetName)
    assertEquals(calendar.get(Calendar.HOUR_OF_DAY), 1)
    assertEquals(calendar.get(Calendar.MINUTE), 2)
    onView(withId(R.id.reset_time_button)).check(matches(isEnabled()))
  }

  @Test
  fun testCancelTimer() {
    every { Preset.findByName(any(), "test") } returns mockk()
    every { WakeUpTimerManager.get(any()) } returns mockk {
      every { presetName } returns "test"
      every { atMillis } returns System.currentTimeMillis() + 10000L
    }

    fragmentScenario.recreate()
    clearMocks(WakeUpTimerManager)
    onView(withId(R.id.reset_time_button))
      .check(matches(isEnabled()))
      .perform(scrollTo(), click())

    verify(exactly = 1) { WakeUpTimerManager.cancel(any()) }
  }
}
