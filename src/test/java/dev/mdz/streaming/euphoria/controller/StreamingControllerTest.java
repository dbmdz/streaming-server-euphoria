package dev.mdz.streaming.euphoria.controller;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mdz.streaming.euphoria.controller.StreamingController.Range;
import org.junit.jupiter.api.Test;

public class StreamingControllerTest {

  @Test
  public void testRange() {
    Range simpleRange = new Range(1, 10);

    long expectedLength = 10;
    long actualLength = simpleRange.length;

    assertThat(actualLength).isEqualTo(expectedLength);
  }
}
