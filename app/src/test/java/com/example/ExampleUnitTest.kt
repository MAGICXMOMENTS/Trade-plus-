package com.example

import com.example.data.firebase.isPlaceholderFirebaseValue
import org.junit.Assert.*
import org.junit.Test

/**
 * Local JVM unit tests for TradePulse configuration guards and utilities.
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun placeholderFirebaseValues_areRejected() {
    assertTrue(isPlaceholderFirebaseValue(null))
    assertTrue(isPlaceholderFirebaseValue(""))
    assertTrue(isPlaceholderFirebaseValue("   "))
    assertTrue(isPlaceholderFirebaseValue("remixed-project-id"))
    assertTrue(isPlaceholderFirebaseValue("YOUR_API_KEY_HERE"))
    assertTrue(isPlaceholderFirebaseValue("placeholder-client-id"))
    assertTrue(isPlaceholderFirebaseValue("default"))
  }

  @Test
  fun validFirebaseValues_areAccepted() {
    assertFalse(isPlaceholderFirebaseValue("1:439496533950:android:a6483eac4cbf57bf08bbef"))
    assertFalse(isPlaceholderFirebaseValue("ai-studio-android-tradepul-3243eaa1-2106-450c-ac8a-d21995dab803"))
    assertFalse(isPlaceholderFirebaseValue("439496533950-9nbk4daot16qgqlbgo7l7ipri5oqcsr1.apps.googleusercontent.com"))
  }
}
