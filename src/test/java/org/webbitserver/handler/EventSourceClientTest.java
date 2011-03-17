package org.webbitserver.handler;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.webbitserver.CometConnection;
import org.webbitserver.CometHandler;
import org.webbitserver.WebServer;
import org.webbitserver.eventsource.EventSource;
import org.webbitserver.eventsource.EventSourceHandler;
import org.webbitserver.eventsource.MessageEvent;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.webbitserver.WebServers.createWebServer;

public class EventSourceClientTest {
    private WebServer webServer;
    private EventSource eventSource;

    @Before
    public void createServer() {
        webServer = createWebServer(59504);
    }

    @After
    public void die() throws IOException, InterruptedException {
        eventSource.close().join();
        webServer.stop().join();
    }

    @Test
    public void canSendAndReadTwoSingleLineMessages() throws Exception {
        assertSentAndReceived(asList("a", "b"));
    }

    @Test
    public void canSendAndReadThreeSingleLineMessages() throws Exception {
        assertSentAndReceived(asList("C", "D", "E"));
    }

    @Test
    public void canSendAndReadOneMultiLineMessages() throws Exception {
        assertSentAndReceived(asList("f\ng\nh"));
    }

    private void assertSentAndReceived(final List<String> messages) throws IOException, InterruptedException {
        webServer
                .add("/es/.*", new CometHandler() {
                    @Override
                    public void onOpen(CometConnection connection) throws Exception {
                        for (String message : messages) {
                            connection.send(message + " " + connection.httpRequest().queryParam("echoThis"));
                        }
                    }

                    @Override
                    public void onClose(CometConnection connection) throws Exception {
                    }

                    @Override
                    public void onMessage(CometConnection connection, String msg) throws Exception {
                    }
                })
                .start();

        final CountDownLatch latch = new CountDownLatch(messages.size());
        eventSource = new EventSource(URI.create("http://localhost:59504/es/hello?echoThis=yo"), new EventSourceHandler() {
            int n = 0;

            @Override
            public void onConnect() {
            }

            @Override
            public void onDisconnect() {
            }

            @Override
            public void onMessage(String event, MessageEvent message) {
                assertEquals(messages.get(n++) + " yo", message.data);
                assertEquals("http://localhost:59504/es/hello?echoThis=yo", message.origin);
                latch.countDown();
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
            }
        });
        eventSource.connect().await();
        assertTrue("Didn't get all messages", latch.await(1000, TimeUnit.MILLISECONDS));
    }
}
