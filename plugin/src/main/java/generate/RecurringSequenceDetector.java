package generate;

import java.util.ArrayList;
import java.util.List;

public class RecurringSequenceDetector {

  private final List<String> buffer = new ArrayList<>();  // To store elements in a sliding window
  private final int repetitionThreshold;  // Threshold for how many times a pattern should repeat

  public RecurringSequenceDetector(int repetitionThreshold) {
    this.repetitionThreshold = repetitionThreshold;
  }

  // Adds the next element in the stream and checks for a recurring pattern crossing the threshold
  public boolean checkForRecursion(String nextElement) {
    buffer.add(nextElement);

    // Try to detect any repeating pattern within the buffer
    for (int patternLength = 1; patternLength <= buffer.size() / 2; patternLength++) {
      if (isRepeatingPattern(patternLength)) {
        return true;  // Recurring pattern detected
      }
    }

    // Keep buffer manageable by removing oldest element if necessary
    if (buffer.size() > repetitionThreshold * 2) {
      buffer.remove(0);
    }

    return false;
  }

  // Helper method to check if there is a repeating pattern of a given length within the buffer
  private boolean isRepeatingPattern(int patternLength) {
    int repeatCount = 1;  // Initialize repeat count

    for (int i = 0; i + 2 * patternLength <= buffer.size(); i += patternLength) {
      boolean isMatch = true;

      // Check if the next segment matches the current pattern segment
      for (int j = 0; j < patternLength; j++) {
        if (!buffer.get(i + j).equals(buffer.get(i + j + patternLength))) {
          isMatch = false;
          break;
        }
      }

      if (isMatch) {
        repeatCount++;
        if (repeatCount >= repetitionThreshold) {
          buffer.clear();
          return true;  // Pattern repeats enough times
        }
      } else {
        break;  // Pattern broke, stop checking this length
      }
    }

    return false;
  }
}
