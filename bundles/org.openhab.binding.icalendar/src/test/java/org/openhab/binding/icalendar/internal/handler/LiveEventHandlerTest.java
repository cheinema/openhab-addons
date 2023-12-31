/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.icalendar.internal.handler;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.ZoneId;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.binding.icalendar.internal.logic.AbstractPresentableCalendar;
import org.openhab.binding.icalendar.internal.logic.Event;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.types.UnDefType;

/**
 * Unit test of {@link LiveEventHandler} with using mock objects.
 *
 * @author Christian Heinemann - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@NonNullByDefault
public class LiveEventHandlerTest {

    private static final ThingUID BRIDGE_UID = new ThingUID("icalendar:calendar:test");
    private static final ThingUID THING_UID = new ThingUID("icalendar:liveevent:test");

    private @Mock @NonNullByDefault({}) TimeZoneProvider tzProviderMock;
    private @Mock @NonNullByDefault({}) ThingHandlerCallback callbackMock;
    private @Mock @NonNullByDefault({}) Bridge bridgeMock;
    private @Mock @NonNullByDefault({}) Thing thingMock;
    private @Mock @NonNullByDefault({}) ICalendarHandler iCalendarHandlerMock;
    private @Mock @NonNullByDefault({}) AbstractPresentableCalendar calendarMock;

    private @NonNullByDefault({}) LiveEventHandler handler;
    private final Configuration configuration = new Configuration();

    @BeforeEach
    public void setUp() {
        doReturn(iCalendarHandlerMock).when(bridgeMock).getHandler();
        doReturn(ThingStatus.ONLINE).when(bridgeMock).getStatus();

        doReturn(bridgeMock).when(callbackMock).getBridge(BRIDGE_UID);

        doReturn(calendarMock).when(iCalendarHandlerMock).getRuntimeCalendar();

        doReturn(BRIDGE_UID).when(thingMock).getBridgeUID();
        doReturn(configuration).when(thingMock).getConfiguration();
        doReturn(ThingStatus.ONLINE).when(thingMock).getStatus();
        doReturn(THING_UID).when(thingMock).getUID();

        doReturn(ZoneId.of("UTC")).when(tzProviderMock).getTimeZone();

        handler = new LiveEventHandler(thingMock, tzProviderMock);
        handler.setCallback(callbackMock);
    }

    @AfterEach
    public void tearDown() {
        handler.dispose();
    }

    @Test
    public void initializeWithNullBridgeShouldSetThingStatusToOffline() {
        doReturn(null).when(callbackMock).getBridge(BRIDGE_UID);

        handler.initialize();

        verify(callbackMock).statusUpdated(eq(thingMock), argThat(arg -> arg.getStatus().equals(ThingStatus.OFFLINE)
                && arg.getStatusDetail() == ThingStatusDetail.CONFIGURATION_ERROR));
    }

    @Test
    public void initializeWithOfflineBridgeShouldSetThingStatusToOffline() {
        doReturn(ThingStatus.OFFLINE).when(bridgeMock).getStatus();

        handler.initialize();

        verify(callbackMock).statusUpdated(eq(thingMock), argThat(arg -> arg.getStatus().equals(ThingStatus.OFFLINE)
                && arg.getStatusDetail() == ThingStatusDetail.BRIDGE_OFFLINE));
    }

    @Test
    public void initializeWithOnlineBridgeShouldSetThingStatusToOnline() {
        doReturn(ThingStatus.ONLINE).when(bridgeMock).getStatus();

        handler.initialize();

        verify(callbackMock).statusUpdated(eq(thingMock), argThat(arg -> arg.getStatus().equals(ThingStatus.UNKNOWN)));
    }

    @Test
    public void initializeShouldAskForCurrentEventWithNowInstant() {
        Instant fakeNow = Instant.parse("2023-12-31T23:59:59Z");

        try (MockedStatic<Instant> mockedStatic = mockStatic(Instant.class)) {
            mockedStatic.when(Instant::now).thenReturn(fakeNow);
            handler.initialize();
        }

        verify(calendarMock).getCurrentEvent(eq(fakeNow), any());
    }

    @Test
    public void initializeWithCurrentCalenderEventShouldSetChannelsWithEventData() {
        Event event = new Event("summary", Instant.parse("2023-12-31T11:23:45Z"), Instant.parse("2023-12-31T12:34:56Z"),
                "description", "location", "comment", "contact");
        doReturn(event).when(calendarMock).getCurrentEvent(any(), any());

        handler.initialize();

        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "current_presence")), eq(OnOffType.ON));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "current_summary")),
                eq(new StringType("summary")));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "current_start")),
                eq(new DateTimeType("2023-12-31T11:23:45Z")));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "current_end")),
                eq(new DateTimeType("2023-12-31T12:34:56Z")));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "current_description")),
                eq(new StringType("description")));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "current_location")),
                eq(new StringType("location")));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "current_comment")),
                eq(new StringType("comment")));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "current_contact")),
                eq(new StringType("contact")));
    }

    @Test
    public void initializeWithoutCurrentCalenderEventShouldResetChannels() {
        doReturn(null).when(calendarMock).getCurrentEvent(any(), any());

        handler.initialize();

        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "current_presence")), eq(OnOffType.OFF));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "current_summary")), eq(UnDefType.UNDEF));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "current_start")), eq(UnDefType.UNDEF));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "current_end")), eq(UnDefType.UNDEF));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "current_description")), eq(UnDefType.UNDEF));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "current_location")), eq(UnDefType.UNDEF));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "current_comment")), eq(UnDefType.UNDEF));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "current_contact")), eq(UnDefType.UNDEF));
    }

    @Test
    public void initializeShouldAskForNextEventWithNowInstant() {
        Instant fakeNow = Instant.parse("2023-12-31T23:59:59Z");

        try (MockedStatic<Instant> mockedStatic = mockStatic(Instant.class)) {
            mockedStatic.when(Instant::now).thenReturn(fakeNow);
            handler.initialize();
        }

        verify(calendarMock).getNextEvent(eq(fakeNow), any());
    }

    @Test
    public void initializeWithNextCalenderEventShouldSetChannelsWithEventData() {
        Event event = new Event("summary", Instant.parse("2023-12-31T11:23:45Z"), Instant.parse("2023-12-31T12:34:56Z"),
                "description", "location", "comment", "contact");
        doReturn(event).when(calendarMock).getNextEvent(any(), any());

        handler.initialize();

        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "next_summary")), eq(new StringType("summary")));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "next_start")),
                eq(new DateTimeType("2023-12-31T11:23:45Z")));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "next_end")),
                eq(new DateTimeType("2023-12-31T12:34:56Z")));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "next_description")),
                eq(new StringType("description")));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "next_location")),
                eq(new StringType("location")));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "next_comment")), eq(new StringType("comment")));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "next_contact")), eq(new StringType("contact")));
    }

    @Test
    public void initializeWithoutNextCalenderEventShouldResetChannels() {
        doReturn(null).when(calendarMock).getNextEvent(any(), any());

        handler.initialize();

        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "next_summary")), eq(UnDefType.UNDEF));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "next_start")), eq(UnDefType.UNDEF));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "next_end")), eq(UnDefType.UNDEF));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "next_description")), eq(UnDefType.UNDEF));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "next_location")), eq(UnDefType.UNDEF));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "next_comment")), eq(UnDefType.UNDEF));
        verify(callbackMock).stateUpdated(eq(new ChannelUID(THING_UID, "next_contact")), eq(UnDefType.UNDEF));
    }
}
