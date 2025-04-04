/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.runtime.operators.window.tvf.slicing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for {@link SliceAssigners.TumblingSliceAssigner}. */
class TumblingSliceAssignerTest extends SliceAssignerTestBase {

    @ParameterizedTest(name = "timezone = {0}")
    @MethodSource("zoneIds")
    void testSliceAssignment(final ZoneId zoneId) {
        SliceAssigner assigner = SliceAssigners.tumbling(0, zoneId, Duration.ofHours(5));

        assertThat(assignSliceEnd(assigner, localMills("1970-01-01T00:00:00", zoneId)))
                .isEqualTo(utcMills("1970-01-01T05:00:00"));
        assertThat(assignSliceEnd(assigner, localMills("1970-01-01T04:59:59.999", zoneId)))
                .isEqualTo(utcMills("1970-01-01T05:00:00"));
        assertThat(assignSliceEnd(assigner, localMills("1970-01-01T05:00:00", zoneId)))
                .isEqualTo(utcMills("1970-01-01T10:00:00"));
    }

    @ParameterizedTest(name = "timezone = {0}")
    @MethodSource("zoneIds")
    void testSliceAssignmentWithOffset(final ZoneId zoneId) {
        SliceAssigner assigner =
                SliceAssigners.tumbling(0, zoneId, Duration.ofHours(5))
                        .withOffset(Duration.ofMillis(100));

        assertThat(assignSliceEnd(assigner, localMills("1970-01-01T00:00:00.1", zoneId)))
                .isEqualTo(utcMills("1970-01-01T05:00:00.1"));
        assertThat(assignSliceEnd(assigner, localMills("1970-01-01T05:00:00.099", zoneId)))
                .isEqualTo(utcMills("1970-01-01T05:00:00.1"));
        assertThat(assignSliceEnd(assigner, localMills("1970-01-01T05:00:00.1", zoneId)))
                .isEqualTo(utcMills("1970-01-01T10:00:00.1"));
    }

    @ParameterizedTest(name = "timezone = {0}")
    @MethodSource("zoneIds")
    void testDstSaving(final ZoneId zoneId) {
        if (!TimeZone.getTimeZone(zoneId).useDaylightTime()) {
            return;
        }
        SliceAssigner assigner = SliceAssigners.tumbling(0, zoneId, Duration.ofHours(4));

        // Los_Angeles local time in epoch mills.
        // The DaylightTime in Los_Angele start at time 2021-03-14 02:00:00
        long epoch1 = 1615708800000L; // 2021-03-14 00:00:00
        long epoch2 = 1615712400000L; // 2021-03-14 01:00:00
        long epoch3 = 1615716000000L; // 2021-03-14 03:00:00, skip one hour (2021-03-14 02:00:00)
        long epoch4 = 1615719600000L; // 2021-03-14 04:00:00

        assertSliceStartEnd("2021-03-14T00:00", "2021-03-14T04:00", epoch1, assigner);
        assertSliceStartEnd("2021-03-14T00:00", "2021-03-14T04:00", epoch2, assigner);
        assertSliceStartEnd("2021-03-14T00:00", "2021-03-14T04:00", epoch3, assigner);
        assertSliceStartEnd("2021-03-14T04:00", "2021-03-14T08:00", epoch4, assigner);

        // Los_Angeles local time in epoch mills.
        // The DaylightTime in Los_Angele end at time 2021-11-07 02:00:00
        long epoch5 = 1636268400000L; // 2021-11-07 00:00:00
        long epoch6 = 1636272000000L; // the first local timestamp 2021-11-07 01:00:00
        long epoch7 = 1636275600000L; // rollback to  2021-11-07 01:00:00
        long epoch8 = 1636279200000L; // 2021-11-07 02:00:00
        long epoch9 = 1636282800000L; // 2021-11-07 03:00:00
        long epoch10 = 1636286400000L; // 2021-11-07 04:00:00

        assertSliceStartEnd("2021-11-07T00:00", "2021-11-07T04:00", epoch5, assigner);
        assertSliceStartEnd("2021-11-07T00:00", "2021-11-07T04:00", epoch6, assigner);
        assertSliceStartEnd("2021-11-07T00:00", "2021-11-07T04:00", epoch7, assigner);
        assertSliceStartEnd("2021-11-07T00:00", "2021-11-07T04:00", epoch8, assigner);
        assertSliceStartEnd("2021-11-07T00:00", "2021-11-07T04:00", epoch9, assigner);
        assertSliceStartEnd("2021-11-07T04:00", "2021-11-07T08:00", epoch10, assigner);
    }

    @ParameterizedTest(name = "timezone = {0}")
    @MethodSource("zoneIds")
    void testGetWindowStart(final ZoneId zoneId) {
        SliceAssigner assigner = SliceAssigners.tumbling(0, zoneId, Duration.ofHours(5));

        assertThat(assigner.getWindowStart(utcMills("1970-01-01T00:00:00")))
                .isEqualTo(utcMills("1969-12-31T19:00:00"));
        assertThat(assigner.getWindowStart(utcMills("1970-01-01T05:00:00")))
                .isEqualTo(utcMills("1970-01-01T00:00:00"));
        assertThat(assigner.getWindowStart(utcMills("1970-01-01T10:00:00")))
                .isEqualTo(utcMills("1970-01-01T05:00:00"));
    }

    @ParameterizedTest(name = "timezone = {0}")
    @MethodSource("zoneIds")
    void testExpiredSlices(final ZoneId zoneId) {
        SliceAssigner assigner = SliceAssigners.tumbling(0, zoneId, Duration.ofHours(5));

        assertThat(expiredSlices(assigner, utcMills("1970-01-01T00:00:00")))
                .containsExactly(utcMills("1970-01-01T00:00:00"));
        assertThat(expiredSlices(assigner, utcMills("1970-01-01T05:00:00")))
                .containsExactly(utcMills("1970-01-01T05:00:00"));
        assertThat(expiredSlices(assigner, utcMills("1970-01-01T10:00:00")))
                .containsExactly(utcMills("1970-01-01T10:00:00"));
    }

    @ParameterizedTest(name = "timezone = {0}")
    @MethodSource("zoneIds")
    void testEventTime(final ZoneId zoneId) {
        SliceAssigner assigner1 = SliceAssigners.tumbling(0, zoneId, Duration.ofHours(5));
        assertThat(assigner1.isEventTime()).isTrue();

        SliceAssigner assigner2 = SliceAssigners.tumbling(-1, zoneId, Duration.ofHours(5));
        assertThat(assigner2.isEventTime()).isFalse();
    }

    @ParameterizedTest(name = "timezone = {0}")
    @MethodSource("zoneIds")
    void testInvalidParameters(final ZoneId zoneId) {
        assertErrorMessage(
                () -> SliceAssigners.tumbling(0, zoneId, Duration.ofSeconds(-1)),
                "Tumbling Window parameters must satisfy size > 0, but got size -1000ms.");

        assertErrorMessage(
                () ->
                        SliceAssigners.tumbling(0, zoneId, Duration.ofSeconds(10))
                                .withOffset(Duration.ofSeconds(20)),
                "Tumbling Window parameters must satisfy abs(offset) < size, but got size 10000ms and offset 20000ms.");

        // should pass
        SliceAssigners.tumbling(0, zoneId, Duration.ofSeconds(10))
                .withOffset(Duration.ofSeconds(-1));
    }
}
