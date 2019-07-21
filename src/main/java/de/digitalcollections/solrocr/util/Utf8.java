/*
 * Vendored from Guava 25 for compatibility with older Solr versions that ship with Guava < 16.0.
 * Also includes a new `decodedLength(ByteBuffer buf)` method to calculate the decoded UTF16 length of UTF8 data.
 *
 * Copyright (C) 2013 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package de.digitalcollections.solrocr.util;

import com.google.common.annotations.Beta;
import com.google.common.annotations.GwtCompatible;
import com.google.common.base.Charsets;

import static com.google.common.base.Preconditions.checkPositionIndexes;
import static java.lang.Character.MAX_SURROGATE;
import static java.lang.Character.MIN_SURROGATE;

/**
 * Low-level, high-performance utility methods related to the {@linkplain Charsets#UTF_8 UTF-8}
 * character encoding. UTF-8 is defined in section D92 of <a
 * href="http://www.unicode.org/versions/Unicode6.2.0/ch03.pdf">The Unicode Standard Core
 * Specification, Chapter 3</a>.
 *
 * <p>The variant of UTF-8 implemented by this class is the restricted definition of UTF-8
 * introduced in Unicode 3.1. One implication of this is that it rejects <a
 * href="http://www.unicode.org/versions/corrigendum1.html">"non-shortest form"</a> byte sequences,
 * even though the JDK decoder may accept them.
 *
 * @author Martin Buchholz
 * @author Clément Roux
 * @since 16.0
 */
@Beta
@GwtCompatible(emulated = true)
public final class Utf8 {
  /**
   * Returns the number of bytes in the UTF-8-encoded form of {@code sequence}. For a string, this
   * method is equivalent to {@code string.getBytes(UTF_8).length}, but is more efficient in both
   * time and space.
   *
   * @throws IllegalArgumentException if {@code sequence} contains ill-formed UTF-16 (unpaired
   *     surrogates)
   */
  public static int encodedLength(CharSequence sequence) {
    // Warning to maintainers: this implementation is highly optimized.
    int utf16Length = sequence.length();
    int utf8Length = utf16Length;
    int i = 0;

    // This loop optimizes for pure ASCII.
    while (i < utf16Length && sequence.charAt(i) < 0x80) {
      i++;
    }

    // This loop optimizes for chars less than 0x800.
    for (; i < utf16Length; i++) {
      char c = sequence.charAt(i);
      if (c < 0x800) {
        utf8Length += ((0x7f - c) >>> 31); // branch free!
      } else {
        utf8Length += encodedLengthGeneral(sequence, i);
        break;
      }
    }

    if (utf8Length < utf16Length) {
      // Necessary and sufficient condition for overflow because of maximum 3x expansion
      throw new IllegalArgumentException(
          "UTF-8 length does not fit in int: " + (utf8Length + (1L << 32)));
    }
    return utf8Length;
  }

  private static int encodedLengthGeneral(CharSequence sequence, int start) {
    int utf16Length = sequence.length();
    int utf8Length = 0;
    for (int i = start; i < utf16Length; i++) {
      char c = sequence.charAt(i);
      if (c < 0x800) {
        utf8Length += (0x7f - c) >>> 31; // branch free!
      } else {
        utf8Length += 2;
        // jdk7+: if (Character.isSurrogate(c)) {
        if (MIN_SURROGATE <= c && c <= MAX_SURROGATE) {
          // Check that we have a well-formed surrogate pair.
          if (Character.codePointAt(sequence, i) == c) {
            throw new IllegalArgumentException(unpairedSurrogateMsg(i));
          }
          i++;
        }
      }
    }
    return utf8Length;
  }

  public static int decodedLength(byte[] buf) {
    int utf8Length = buf.length;
    int charLength = utf8Length;
    int i = 0;

    // Optimized for pure ASCII, no length difference
    while (i < utf8Length && (buf[i] & 0xFF) < 0x80) {
      i++;
    }

    while (i < utf8Length) {
      int hiBits = ((buf[i] & 0xFF) >> 4) & 0xF;
      switch (hiBits) {
        case 0: case 1: case 2: case 3: case 4: case 5: case 6: case 7: case 8:
          // U+0000 to U+0080 are 1 byte UTF-8 and 1 char, so no changes neccessary
          i++;
          break;
        case 0xC: case 0xD:
          // U+0080 to U+07FF are 2 bytes UTF-8 and 1 char, so the char length is decrement by one
          charLength--;
          i += 2;
          break;
        case 0xE:
          // U+0800 to U+FFFF are 3 bytes UTF-8 and 1 char, so the char length is decremented by two
          charLength -= 2;
          i += 3;
          break;
        case 0xF:
          // U+10000 to U+10FFFF are 4 byts UTF-8 and 2 char, so the char length is decremented by two
          charLength -= 2;
          i += 4;
          break;
        default:
          throw new RuntimeException("Illegal UTF8 starting byte.");
      }
    }
    return charLength;
  }

  /**
   * Returns the number of bytes required to encode the given Unicode codepoint into UTF8.
   */
  public static int encodedLength(int codePoint) {
    if (codePoint < 0x80) {
      return 1;
    } else if (codePoint < 0x800) {
      return 2;
    } else if (codePoint < 0x10000 || codePoint >= 0x110000) {
      return 3;
    } else {
      return 4;
    }
  }

  public static int encodedLength(char c) {
    if (c < 0x80) {
      return 1;
    } else if (c < 0x800) {
      return ((0x7f - c) >>> 31);
    } else {
      return 2;
    }
  }

  /**
   * Returns {@code true} if {@code bytes} is a <i>well-formed</i> UTF-8 byte sequence according to
   * Unicode 6.0. Note that this is a stronger criterion than simply whether the bytes can be
   * decoded. For example, some versions of the JDK decoder will accept "non-shortest form" byte
   * sequences, but encoding never reproduces these. Such byte sequences are <i>not</i> considered
   * well-formed.
   *
   * <p>This method returns {@code true} if and only if {@code Arrays.equals(bytes, new
   * String(bytes, UTF_8).getBytes(UTF_8))} does, but is more efficient in both time and space.
   */
  public static boolean isWellFormed(byte[] bytes) {
    return isWellFormed(bytes, 0, bytes.length);
  }

  /**
   * Returns whether the given byte array slice is a well-formed UTF-8 byte sequence, as defined by
   * {@link #isWellFormed(byte[])}. Note that this can be false even when {@code
   * isWellFormed(bytes)} is true.
   *
   * @param bytes the input buffer
   * @param off the offset in the buffer of the first byte to read
   * @param len the number of bytes to read from the buffer
   */
  public static boolean isWellFormed(byte[] bytes, int off, int len) {
    int end = off + len;
    checkPositionIndexes(off, end, bytes.length);
    // Look for the first non-ASCII character.
    for (int i = off; i < end; i++) {
      if (bytes[i] < 0) {
        return isWellFormedSlowPath(bytes, i, end);
      }
    }
    return true;
  }

  private static boolean isWellFormedSlowPath(byte[] bytes, int off, int end) {
    int index = off;
    while (true) {
      int byte1;

      // Optimize for interior runs of ASCII bytes.
      do {
        if (index >= end) {
          return true;
        }
      } while ((byte1 = bytes[index++]) >= 0);

      if (byte1 < (byte) 0xE0) {
        // Two-byte form.
        if (index == end) {
          return false;
        }
        // Simultaneously check for illegal trailing-byte in leading position
        // and overlong 2-byte form.
        if (byte1 < (byte) 0xC2 || bytes[index++] > (byte) 0xBF) {
          return false;
        }
      } else if (byte1 < (byte) 0xF0) {
        // Three-byte form.
        if (index + 1 >= end) {
          return false;
        }
        int byte2 = bytes[index++];
        if (byte2 > (byte) 0xBF
            // Overlong? 5 most significant bits must not all be zero.
            || (byte1 == (byte) 0xE0 && byte2 < (byte) 0xA0)
            // Check for illegal surrogate codepoints.
            || (byte1 == (byte) 0xED && (byte) 0xA0 <= byte2)
            // Third byte trailing-byte test.
            || bytes[index++] > (byte) 0xBF) {
          return false;
        }
      } else {
        // Four-byte form.
        if (index + 2 >= end) {
          return false;
        }
        int byte2 = bytes[index++];
        if (byte2 > (byte) 0xBF
            // Check that 1 <= plane <= 16. Tricky optimized form of:
            // if (byte1 > (byte) 0xF4
            //     || byte1 == (byte) 0xF0 && byte2 < (byte) 0x90
            //     || byte1 == (byte) 0xF4 && byte2 > (byte) 0x8F)
            || (((byte1 << 28) + (byte2 - (byte) 0x90)) >> 30) != 0
            // Third byte trailing-byte test
            || bytes[index++] > (byte) 0xBF
            // Fourth byte trailing-byte test
            || bytes[index++] > (byte) 0xBF) {
          return false;
        }
      }
    }
  }

  private static String unpairedSurrogateMsg(int i) {
    return "Unpaired surrogate at index " + i;
  }

  private Utf8() {}
}
