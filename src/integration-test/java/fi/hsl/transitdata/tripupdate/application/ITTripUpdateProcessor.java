package fi.hsl.transitdata.tripupdate.application;

import com.google.transit.realtime.GtfsRealtime;
import fi.hsl.common.gtfsrt.FeedMessageFactory;
import fi.hsl.common.pulsar.*;
import fi.hsl.common.transitdata.MockDataUtils;
import fi.hsl.common.transitdata.PubtransFactory;
import fi.hsl.common.transitdata.TransitdataProperties;
import fi.hsl.common.transitdata.proto.InternalMessages;
import fi.hsl.common.transitdata.proto.PubtransTableProtos;
import fi.hsl.transitdata.tripupdate.gtfsrt.GtfsRtFactory;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.TypedMessageBuilder;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

public class ITTripUpdateProcessor extends ITBaseTestSuite {

    final static DateTimeFormatter gtfsRtDatePattern = DateTimeFormatter.ofPattern("yyyyMMdd");
    final static DateTimeFormatter gtfsRtTimePattern =  DateTimeFormatter.ofPattern("HH:mm:ss");

    final long dvjId = 1234567890L;
    final String route = "7575";
    final int joreDirection = PubtransFactory.JORE_DIRECTION_ID_INBOUND;
    final int gtfsRtDirection = PubtransFactory.joreDirectionToGtfsDirection(joreDirection);
    final String date = "2020-12-24";
    final String time = "18:00:00";
    final LocalDateTime dateTime = LocalDateTime.parse(date + "T" + time);
    final int stopId = 0;
    final int stopSequence = MockDataUtils.generateValidStopSequenceId();

    @Test
    public void testValidCancellationWithDirection1() throws Exception {
        final String testId = "-valid-cancel-joredir-1";
        testValidCancellation(route, route,1, testId);
    }

    @Test
    public void testValidCancellationWithDirection2() throws Exception {
        final String testId = "-valid-cancel-joredir-2";
        testValidCancellation(route, route,2, testId);
    }

    @Test
    public void testValidCancellationRouteNameFormatting() throws Exception {
        final String testId = "-valid-cancel-routename-formatting";
        String joreRouteName = "4250D8";
        String formattedRouteName = "4250D";
        testValidCancellation(joreRouteName, formattedRouteName, joreDirection, testId);
    }

    @Test
    public void testCancellationWithRunningStatus() throws Exception {
        final String testId = "-valid-cancel-running-status";
        final InternalMessages.TripCancellation.Status runningStatus = InternalMessages.TripCancellation.Status.RUNNING;
        testValidCancellation(route, route,2, testId, runningStatus);
    }


    private void testValidCancellation(String routeName, String expectedRouteName, int joreDir, String testId) throws Exception {
        testValidCancellation(routeName, expectedRouteName, joreDir, testId, InternalMessages.TripCancellation.Status.CANCELED);
    }

    private void testValidCancellation(String routeName, String expectedRouteName, int joreDir, String testId, InternalMessages.TripCancellation.Status status) throws Exception {
        int gtfsDir = joreDir - 1;
        TestPipeline.TestLogic logic = new TestPipeline.TestLogic() {
            @Override
            public void testImpl(TestPipeline.TestContext context) throws Exception {

                final long ts = System.currentTimeMillis();
                InternalMessages.TripCancellation cancellation = MockDataUtils.mockTripCancellation(dvjId, routeName, joreDir, dateTime, status);
                sendPubtransSourcePulsarMessage(context.source, new PubtransPulsarMessageData.CancellationPulsarMessageData(cancellation, ts, dvjId));

                logger.info("Message sent, reading it back");

                Message<byte[]> received = TestPipeline.readOutputMessage(context);
                assertNotNull(received);

                validatePulsarProperties(received, Long.toString(dvjId), ts, TransitdataProperties.ProtobufSchema.GTFS_TripUpdate);

                GtfsRealtime.FeedMessage feedMessage = GtfsRealtime.FeedMessage.parseFrom(received.getData());

                validateCancellationPayload(feedMessage, ts, expectedRouteName, gtfsDir, dateTime, status);
                logger.info("Message read back, all good");

                TestPipeline.validateAcks(1, context);
            }
        };
        PulsarApplication testApp = createPulsarApp("integration-test.conf", testId);
        IMessageHandler handlerToTest = new MessageRouter(testApp.getContext());
        testPulsarMessageHandler(handlerToTest, testApp, logic, testId);
    }

    @Test
    public void testCancellationWithGtfsRtDirection() throws Exception {
        //InternalMessages are in Jore format 1-2, gtfs-rt in 0-1
        final int invalidJoreDirection = 0;
        InternalMessages.TripCancellation cancellation = MockDataUtils.mockTripCancellation(dvjId, route, invalidJoreDirection, dateTime);
        PubtransPulsarMessageData data = new PubtransPulsarMessageData.CancellationPulsarMessageData(cancellation, System.currentTimeMillis(), dvjId);

        testInvalidInput(data, "-test-gtfs-dir");
    }

    @Test
    public void testCancellationWithInvalidDirection() throws Exception {
        //InternalMessages are in Jore format 1-2, gtfs-rt in 0-1
        InternalMessages.TripCancellation cancellation = MockDataUtils.mockTripCancellation(dvjId, route, 10, dateTime);
        PubtransPulsarMessageData data = new PubtransPulsarMessageData.CancellationPulsarMessageData(cancellation, System.currentTimeMillis(), dvjId);
        testInvalidInput(data, "-test-invalid-dir");
    }

    @Test
    public void testCancellationWithTrainRoute() throws Exception {
        String trainRoute = "3001";
        InternalMessages.TripCancellation cancellation = MockDataUtils.mockTripCancellation(dvjId, trainRoute, joreDirection, dateTime);
        PubtransPulsarMessageData data = new PubtransPulsarMessageData.CancellationPulsarMessageData(cancellation, System.currentTimeMillis(), dvjId);
        testInvalidInput(data, "-test-train-route");
    }

    /**
     * Convenience method for running tests that should always be filtered by the TripUpdateProcessor,
     * meaning no Gtfs-RT should ever be received. However we should get an ack to producer.
     *
     * @throws Exception
     */
    private void testInvalidInput(final PubtransPulsarMessageData data, String testId) throws Exception {
        TestPipeline.TestLogic logic = new TestPipeline.TestLogic() {
            @Override
            public void testImpl(TestPipeline.TestContext context) throws Exception {
                final long ts = System.currentTimeMillis();
                sendPubtransSourcePulsarMessage(context.source, data);
                logger.info("Message sent, reading it back");

                Message<byte[]> received = TestPipeline.readOutputMessage(context);
                //There should not be any output, read should just timeout
                assertNull(received);
                TestPipeline.validateAcks(1, context); //sender should still get acks.
            }
        };
        PulsarApplication testApp = createPulsarApp("integration-test.conf", testId);
        IMessageHandler handlerToTest = new MessageRouter(testApp.getContext());
        testPulsarMessageHandler(handlerToTest, testApp, logic, testId);
    }

    private void validateCancellationPayload(final GtfsRealtime.FeedMessage feedMessage,
                                             final long eventTimeMs,
                                             final String routeId,
                                             final int gtfsRtDirection,
                                             final LocalDateTime eventTimeLocal,
                                             final InternalMessages.TripCancellation.Status status) {
        assertNotNull(feedMessage);
        assertEquals(1, feedMessage.getEntityCount());

        final long gtfsRtEventTime = eventTimeMs / 1000;//GTFS-RT outputs seconds.
        assertEquals(gtfsRtEventTime, feedMessage.getHeader().getTimestamp());

        final GtfsRealtime.TripUpdate tripUpdate = feedMessage.getEntity(0).getTripUpdate();
        assertEquals(0, tripUpdate.getStopTimeUpdateCount());
        assertEquals(gtfsRtEventTime, tripUpdate.getTimestamp());

        final GtfsRealtime.TripDescriptor trip = tripUpdate.getTrip();
        final GtfsRealtime.TripDescriptor.ScheduleRelationship expectedStatus =
                status == InternalMessages.TripCancellation.Status.CANCELED ?
                        GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED :
                        GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED;

        assertEquals(expectedStatus, trip.getScheduleRelationship());
        assertEquals(routeId, trip.getRouteId());
        assertEquals(gtfsRtDirection, trip.getDirectionId());

        String localDate = gtfsRtDatePattern.format(eventTimeLocal);
        String localTime = gtfsRtTimePattern.format(eventTimeLocal);

        assertEquals(localDate, trip.getStartDate());
        assertEquals(localTime, trip.getStartTime());
    }

    @Test
    public void testTogglingCancellationStatus() throws Exception {
        final InternalMessages.TripCancellation cancellation = MockDataUtils.mockTripCancellation(dvjId, route, joreDirection, dateTime, InternalMessages.TripCancellation.Status.CANCELED);

        long startTimeEpochMs = dateTime.toEpochSecond(ZoneOffset.UTC) * 1000;
        PubtransTableProtos.Common common = MockDataUtils.mockCommon(dvjId, stopSequence, startTimeEpochMs).build();
        PubtransTableProtos.DOITripInfo mockTripInfo = MockDataUtils.mockDOITripInfo(dvjId, route, stopId, startTimeEpochMs);

        final InternalMessages.StopEstimate estimate = PubtransFactory.createStopEstimate(common, mockTripInfo, InternalMessages.StopEstimate.Type.DEPARTURE);
        final InternalMessages.TripCancellation running = MockDataUtils.mockTripCancellation(dvjId, route, joreDirection, dateTime, InternalMessages.TripCancellation.Status.RUNNING);

        TestPipeline.TestLogic logic = new TestPipeline.TestLogic() {
            @Override
            public void testImpl(TestPipeline.TestContext context) throws Exception {
                {
                    final long ts = System.currentTimeMillis();
                    sendPubtransSourcePulsarMessage(context.source, new PubtransPulsarMessageData.CancellationPulsarMessageData(cancellation, ts, dvjId));

                    logger.info("Cancellation sent, reading it back");

                    Message<byte[]> received = TestPipeline.readOutputMessage(context);
                    assertNotNull(received);
                    validatePulsarProperties(received, Long.toString(dvjId), ts, TransitdataProperties.ProtobufSchema.GTFS_TripUpdate);
                    validateScheduledRelationshipAndStopEstimateCountFromGtfsRtFeedMessage(received.getData(),
                            GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED, 0);
                }
                {
                    final long ts = System.currentTimeMillis();
                    sendPubtransSourcePulsarMessage(context.source, new PubtransPulsarMessageData.StopEstimateMessageData(estimate, ts, dvjId));

                    logger.info("Estimate sent, reading it back");

                    Message<byte[]> received = TestPipeline.readOutputMessage(context);
                    // Trip should be cancelled so we shouldn't receive an estimate.
                    assertNull(received);
                }
                {
                    final long ts = System.currentTimeMillis();
                    sendPubtransSourcePulsarMessage(context.source, new PubtransPulsarMessageData.CancellationPulsarMessageData(running, ts, dvjId));

                    logger.info("Running sent, reading it back");

                    Message<byte[]> received = TestPipeline.readOutputMessage(context);
                    // Now we should receive the estimate with correct status and it should include the estimate sent earlier.
                    assertNotNull(received);
                    validatePulsarProperties(received, Long.toString(dvjId), ts, TransitdataProperties.ProtobufSchema.GTFS_TripUpdate);
                    validateScheduledRelationshipAndStopEstimateCountFromGtfsRtFeedMessage(received.getData(),
                            GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED, 1);

                }
                {
                    final long ts = System.currentTimeMillis();
                    sendPubtransSourcePulsarMessage(context.source, new PubtransPulsarMessageData.CancellationPulsarMessageData(cancellation, ts, dvjId));

                    logger.info("Cancelling again, reading it back");

                    Message<byte[]> received = TestPipeline.readOutputMessage(context);
                    assertNotNull(received);
                    validatePulsarProperties(received, Long.toString(dvjId), ts, TransitdataProperties.ProtobufSchema.GTFS_TripUpdate);
                    //Cancellation should strip out the estimates again.
                    validateScheduledRelationshipAndStopEstimateCountFromGtfsRtFeedMessage(received.getData(),
                            GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED, 0);
                }
            }
        };
        final String testId = "running-after-cancelled-with-estimates";
        PulsarApplication testApp = createPulsarApp("integration-test.conf", testId);
        IMessageHandler handlerToTest = new MessageRouter(testApp.getContext());
        testPulsarMessageHandler(handlerToTest, testApp, logic, testId);
    }

    private void validateScheduledRelationshipAndStopEstimateCountFromGtfsRtFeedMessage(byte[] received,
                                                                                        GtfsRealtime.TripDescriptor.ScheduleRelationship expectedStatus,
                                                                                        int expectedStopEstimateCount
                                                                                        ) throws Exception {
        GtfsRealtime.FeedMessage feedMessage = GtfsRealtime.FeedMessage.parseFrom(received);
        assertEquals(expectedStatus, feedMessage.getEntity(0).getTripUpdate().getTrip().getScheduleRelationship());
        assertEquals(expectedStopEstimateCount, feedMessage.getEntity(0).getTripUpdate().getStopTimeUpdateCount());
    }

    @Test
    public void testValidArrivalStopEvent() throws Exception {
        long now = System.currentTimeMillis();
        long eventTime = now + 5 * 60000; // event to happen five minutes from now
        InternalMessages.StopEstimate arrival = MockDataUtils.mockStopEstimate(dvjId,
                InternalMessages.StopEstimate.Type.ARRIVAL,
               stopId, stopSequence, eventTime);
        PubtransPulsarMessageData.StopEstimateMessageData msg = new PubtransPulsarMessageData.StopEstimateMessageData(arrival, now, dvjId);
        testValidStopEvent(msg, "-test-valid-arrival");
    }

    @Test
    public void testValidDepartureStopEvent() throws Exception {
        long now = System.currentTimeMillis();
        long eventTime = now + 3 * 60000; // event to happen three minutes from now
        InternalMessages.StopEstimate departure = MockDataUtils.mockStopEstimate(dvjId,
                InternalMessages.StopEstimate.Type.DEPARTURE,
                stopId, stopSequence, eventTime);

        PubtransPulsarMessageData.StopEstimateMessageData msg = new PubtransPulsarMessageData.StopEstimateMessageData(departure, now, dvjId);
        testValidStopEvent(msg, "-test-valid-departure");
    }

    private void testValidStopEvent(PubtransPulsarMessageData sourceMsg, String testId) throws Exception {
        TestPipeline.TestLogic logic = new TestPipeline.TestLogic() {
            @Override
            public void testImpl(TestPipeline.TestContext context) throws Exception {

                sendPubtransSourcePulsarMessage(context.source, sourceMsg);
                logger.info("Message sent, reading it back");

                Message<byte[]> received = TestPipeline.readOutputMessage(context);
                assertNotNull(received);

                String expectedKey = Long.toString(sourceMsg.dvjId);
                validatePulsarProperties(received, expectedKey, sourceMsg.eventTime.get(), TransitdataProperties.ProtobufSchema.GTFS_TripUpdate);

                GtfsRealtime.FeedMessage feedMessage = GtfsRealtime.FeedMessage.parseFrom(received.getData());
                assertNotNull(feedMessage);
                assertEquals(1, feedMessage.getEntityCount());
                GtfsRealtime.TripUpdate tu = feedMessage.getEntity(0).getTripUpdate();
                assertNotNull(tu);

                assertEquals(1, tu.getStopTimeUpdateCount());
                GtfsRealtime.TripUpdate.StopTimeUpdate update = tu.getStopTimeUpdate(0);
                assertNotNull(update);

                assertIfArrivalAndDepartureDiffer(update);

                assertFalse(update.hasStopSequence()); // We don't include stopSequence since it's our via-points etc can confuse the feed.
                assertTrue(update.hasStopId()); // TODO add check for StopId

                assertTrue(tu.hasTrip());
                //TODO add more checks for the payload

                logger.info("Message read back, all good");
                TestPipeline.validateAcks(1, context);
            }
        };

        PulsarApplication testApp = createPulsarApp("integration-test.conf", testId);
        IMessageHandler handlerToTest = new MessageRouter(testApp.getContext());
        testPulsarMessageHandler(handlerToTest, testApp, logic, testId);
    }

    private void assertIfArrivalAndDepartureDiffer(GtfsRealtime.TripUpdate.StopTimeUpdate update) {
        // We currently always have both StopTimeUpdates (arrival and departure) for OpenTripPlanner,
        // If only one of them is sent we add identical one to the other field
        assertTrue(update.hasArrival());
        assertTrue(update.hasDeparture());
        assertEquals(update.getArrival(), update.getDeparture());
    }


    @Test
    public void testGarbageInput() throws Exception {
        final String testId = "-test-garbage-input";
        //TripUpdateProcessor should handle garbage data and process only the relevant messages
        ArrayList<PulsarMessageData> input = new ArrayList<>();
        ArrayList<PulsarMessageData> expectedOutput = new ArrayList<>();

        //Add garbage which should just be ignored
        PulsarMessageData nullData = new PulsarMessageData("".getBytes(), System.currentTimeMillis());
        input.add(nullData);
        PulsarMessageData dummyData = new PulsarMessageData("dummy-content".getBytes(), System.currentTimeMillis(), "invalid-key");
        input.add(dummyData);

        //Then a real message
        final long now = System.currentTimeMillis();
        final long eventTime = now + 60000; //One minute from now
        InternalMessages.StopEstimate arrival = MockDataUtils.mockStopEstimate(dvjId,
                InternalMessages.StopEstimate.Type.ARRIVAL,
                stopId, stopSequence, eventTime);

        PubtransPulsarMessageData.StopEstimateMessageData validMsg = new PubtransPulsarMessageData.StopEstimateMessageData(arrival, now, dvjId);
        input.add(validMsg);
        GtfsRealtime.FeedMessage asFeedMessage = toGtfsRt(validMsg);

        //Expected output is GTFS-RT TripUpdate
        Map<String, String> outputProperties = new HashMap<>();
        outputProperties.put(TransitdataProperties.KEY_PROTOBUF_SCHEMA, TransitdataProperties.ProtobufSchema.GTFS_TripUpdate.toString());
        final long expectedTimestamp = asFeedMessage.getHeader().getTimestamp();
        final String expectedKey = validMsg.key.get();
        PulsarMessageData validOutput = new PulsarMessageData(asFeedMessage.toByteArray(), expectedTimestamp, expectedKey, outputProperties);
        expectedOutput.add(validOutput);

        TestPipeline.MultiMessageTestLogic logic = new TestPipeline.MultiMessageTestLogic(input, expectedOutput) {
            @Override
            public void validateMessage(PulsarMessageData expected, PulsarMessageData received) {
                try {
                    assertNotNull(expected);
                    assertNotNull(received);
                    assertEquals(TransitdataProperties.ProtobufSchema.GTFS_TripUpdate.toString(), received.properties.get(TransitdataProperties.KEY_PROTOBUF_SCHEMA));
                    assertEquals(validMsg.key.get(), received.key.get());

                    long expectedPulsarTimestampInMs = validMsg.eventTime.get();
                    assertEquals(expectedPulsarTimestampInMs, (long)received.eventTime.get()); // This should be in ms

                    GtfsRealtime.FeedMessage feedMessage = GtfsRealtime.FeedMessage.parseFrom(received.payload);
                    assertNotNull(feedMessage);
                    assertEquals(expectedPulsarTimestampInMs / 1000, feedMessage.getHeader().getTimestamp());

                    GtfsRealtime.FeedEntity entity = feedMessage.getEntity(0);
                    assertTrue(entity.hasTripUpdate());
                    assertFalse(entity.hasAlert());
                    assertFalse(entity.hasVehicle());
                    assertEquals(Long.toString(validMsg.dvjId), entity.getId());
                }
                catch (Exception e) {
                    logger.error("Failed to validate message", e);
                    assert(false);
                }
            }
        };

        PulsarApplication testApp = createPulsarApp("integration-test.conf", testId);
        IMessageHandler handlerToTest = new MessageRouter(testApp.getContext());
        testPulsarMessageHandler(handlerToTest, testApp, logic, testId);
    }

    private GtfsRealtime.FeedMessage toGtfsRt(PubtransPulsarMessageData.StopEstimateMessageData estimate) throws Exception {

        GtfsRealtime.TripUpdate tu = GtfsRtFactory.newTripUpdate(estimate.actualPayload);

        Long timestampAsSecs = estimate.eventTime.map(utcMs -> utcMs / 1000).get();
        GtfsRealtime.FeedMessage feedMessage = FeedMessageFactory.createDifferentialFeedMessage(Long.toString(estimate.dvjId), tu, timestampAsSecs);
        return feedMessage;
    }

    static void sendPubtransSourcePulsarMessage(Producer<byte[]> producer, PubtransPulsarMessageData data) throws PulsarClientException {
        sendPulsarMessage(producer, data.dvjId, data.payload, data.eventTime.get(), data.schema);
    }

    static void sendPulsarMessage(Producer<byte[]> producer, long dvjId, byte[] payload, long timestampEpochMs, TransitdataProperties.ProtobufSchema schema) throws PulsarClientException {
        String dvjIdAsString = Long.toString(dvjId);
        TypedMessageBuilder<byte[]> builder = producer.newMessage().value(payload)
                .eventTime(timestampEpochMs)
                .key(dvjIdAsString)
                .property(TransitdataProperties.KEY_PROTOBUF_SCHEMA, schema.toString());

        builder.send();
    }
}
