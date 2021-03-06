package fi.hsl.transitdata.tripupdate.gtfsrt;

import com.google.transit.realtime.GtfsRealtime;
import fi.hsl.common.transitdata.MockDataUtils;
import fi.hsl.common.transitdata.proto.InternalMessages;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

public class GtfsRtFactoryTest {

    @Test
    public void newTripUpdateFromStopEventWithLetterAndNumberIsRenamedProperly() throws Exception {
        InternalMessages.StopEstimate stopEstimate = MockDataUtils.mockStopEstimate("1010H4");
        GtfsRealtime.TripUpdate tripUpdate = GtfsRtFactory.newTripUpdate(stopEstimate);

        assertEquals("1010H", tripUpdate.getTrip().getRouteId());
    }

    @Test
    public void newTripUpdateFromStopEventWithSpaceAndNumberIsRenamedProperly() throws Exception {

        InternalMessages.StopEstimate stopEstimate = MockDataUtils.mockStopEstimate("1010 3");
        GtfsRealtime.TripUpdate tripUpdate = GtfsRtFactory.newTripUpdate(stopEstimate);

        assertEquals("1010", tripUpdate.getTrip().getRouteId());
    }

    @Test
    public void newTripUpdateFromStopEventWithFourCharactersIsNotRenamed() throws Exception {

        InternalMessages.StopEstimate stopEstimate = MockDataUtils.mockStopEstimate("1010");
        GtfsRealtime.TripUpdate tripUpdate = GtfsRtFactory.newTripUpdate(stopEstimate);

        assertEquals("1010", tripUpdate.getTrip().getRouteId());
    }

    @Test
    public void newTripUpdateFromStopEventWithOneLetterIsNotRenamed() throws Exception {

        InternalMessages.StopEstimate stopEstimate = MockDataUtils.mockStopEstimate("1010H");
        GtfsRealtime.TripUpdate tripUpdate = GtfsRtFactory.newTripUpdate(stopEstimate);

        assertEquals("1010H", tripUpdate.getTrip().getRouteId());
    }

    @Test
    public void newTripUpdateFromStopEventWithTwoLettersIsNotRenamed() throws Exception {

        InternalMessages.StopEstimate stopEstimate = MockDataUtils.mockStopEstimate("1010HK");
        GtfsRealtime.TripUpdate tripUpdate = GtfsRtFactory.newTripUpdate(stopEstimate);

        assertEquals("1010HK", tripUpdate.getTrip().getRouteId());
    }

    @Test
    public void newTripUpdateFromTripCancellationWithLetterAndNumberIsRenamedProperly() throws Exception {

        InternalMessages.TripCancellation tripCancellation = MockDataUtils.mockTripCancellation("1010H4");
        GtfsRealtime.TripUpdate tripUpdate = GtfsRtFactory.newTripUpdate(tripCancellation, 1542096708);

        assertEquals("1010H", tripUpdate.getTrip().getRouteId());
    }

    @Test
    public void newTripUpdateFromTripCancellationWithSpaceAndNumberIsRenamedProperly() throws Exception {

        InternalMessages.TripCancellation tripCancellation = MockDataUtils.mockTripCancellation("1010 3");
        GtfsRealtime.TripUpdate tripUpdate = GtfsRtFactory.newTripUpdate(tripCancellation, 1542096708);

        assertEquals("1010", tripUpdate.getTrip().getRouteId());
    }

    @Test
    public void newTripUpdateFromTripCancellationWithFourCharactersIsNotRenamed() throws Exception {

        InternalMessages.TripCancellation tripCancellation = MockDataUtils.mockTripCancellation("1010");
        GtfsRealtime.TripUpdate tripUpdate = GtfsRtFactory.newTripUpdate(tripCancellation, 1542096708);

        assertEquals("1010", tripUpdate.getTrip().getRouteId());
    }

    @Test
    public void newTripUpdateFromTripCancellationWithOneLetterIsNotRenamed() throws Exception {

        InternalMessages.TripCancellation tripCancellation = MockDataUtils.mockTripCancellation("1010H");
        GtfsRealtime.TripUpdate tripUpdate = GtfsRtFactory.newTripUpdate(tripCancellation, 1542096708);

        assertEquals("1010H", tripUpdate.getTrip().getRouteId());
    }

    @Test
    public void newTripUpdateFromTripCancellationWithTwoLettersIsNotRenamed() throws Exception {

        InternalMessages.TripCancellation tripCancellation = MockDataUtils.mockTripCancellation("1010HK");
        GtfsRealtime.TripUpdate tripUpdate = GtfsRtFactory.newTripUpdate(tripCancellation, 1542096708);

        assertEquals("1010HK", tripUpdate.getTrip().getRouteId());
    }
}
