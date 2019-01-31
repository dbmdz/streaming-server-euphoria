package de.digitalcollections.streaming.euphoria.controller;

import de.digitalcollections.streaming.euphoria.controller.StreamingController.Range;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StreamingControllerTest {

  @Test
  public void testRange() {
    Range simpleRange = new Range(1, 10);

    long expectedLength = 10;
    long actualLength = simpleRange.length;

    assertThat(actualLength).isEqualTo(expectedLength);
  }
}
