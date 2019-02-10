package blog.devrandom.http;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.StringTokenizer;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpServerTest {

    @Mock
    private Socket client;

    private ByteArrayOutputStream outputStream;

    private HttpServer server;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        outputStream = new ByteArrayOutputStream();
        when(client.getOutputStream()).thenReturn(outputStream);
        server = new HttpServer(client);
    }

    @Test
    public void request_index_success() throws IOException {
        // setup the request
        prepareIncomingRequestStream(generateIncomingRequest("GET", "/"));

        // test
        server.run();

        // verify
        StringTokenizer tokenizer = new StringTokenizer(new String(outputStream.toByteArray()));
        assertEquals("HTTP/1.1", tokenizer.nextToken());
        assertEquals("200", tokenizer.nextToken());
    }

    @Test
    public void request_otherResource_notFound() throws IOException {
        // setup
        prepareIncomingRequestStream(generateIncomingRequest("GET", "/favicon.ico"));

        // test
        server.run();

        // verify
        StringTokenizer tokenizer = new StringTokenizer(new String(outputStream.toByteArray()));
        assertEquals("HTTP/1.1", tokenizer.nextToken());
        assertEquals("404", tokenizer.nextToken());
    }

    @Test
    public void request_post_noImplemented() throws IOException {
        // setup
        prepareIncomingRequestStream(generateIncomingRequest("POST", "/"));

        // test
        server.run();

        //verify
        StringTokenizer tokenizer = new StringTokenizer(new String(outputStream.toByteArray()));
        assertEquals("HTTP/1.1", tokenizer.nextToken());
        assertEquals("501", tokenizer.nextToken());
    }

    private String generateIncomingRequest(String method, String resource) {
        return method + " " + resource + " HTTP/1.1\n" +
                "Host: localhost:8080\n" +
                "User-Agent: curl/7.61.1\n" +
                "Accept: */*\n";
    }

    private void prepareIncomingRequestStream(String stream) throws IOException {
        InputStream inputStream = new ByteArrayInputStream(stream.getBytes());
        when(client.getInputStream()).thenReturn(inputStream);
    }
}
