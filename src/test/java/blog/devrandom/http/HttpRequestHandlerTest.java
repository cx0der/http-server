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
import java.net.InetAddress;
import java.net.Socket;
import java.util.Properties;
import java.util.StringTokenizer;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpRequestHandlerTest {

    @Mock
    private Socket client;

    private ByteArrayOutputStream outputStream;

    private HttpRequestHandler requestHandler;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        outputStream = new ByteArrayOutputStream();
        when(client.getOutputStream()).thenReturn(outputStream);
        when(client.getInetAddress()).thenReturn(InetAddress.getByName("localhost"));
        Properties props = new Properties();
        props.put(HttpRequestHandler.ROOT_PARAM, "server");
        requestHandler = new HttpRequestHandler(client, props, System.out);
    }

    @Test
    public void request_index_success() throws IOException {
        // setup the request
        prepareIncomingRequestStream(generateIncomingRequest("GET", "/"));

        // test
        requestHandler.run();

        // verify
        StringTokenizer tokenizer = new StringTokenizer(new String(outputStream.toByteArray()));
        assertEquals("HTTP/1.1", tokenizer.nextToken());
        assertEquals("200", tokenizer.nextToken());
    }

    @Test
    public void request_subdirectory_success() throws IOException {
        // setup the request
        prepareIncomingRequestStream(generateIncomingRequest("GET", "/css/main.css"));

        // test
        requestHandler.run();

        // verify
        StringTokenizer tokenizer = new StringTokenizer(new String(outputStream.toByteArray()));
        assertEquals("HTTP/1.1", tokenizer.nextToken());
        assertEquals("200", tokenizer.nextToken());
    }

    @Test
    public void request_subdirectory_not_found() throws IOException {
        // setup the request
        prepareIncomingRequestStream(generateIncomingRequest("GET", "/css"));

        // test
        requestHandler.run();

        // verify
        StringTokenizer tokenizer = new StringTokenizer(new String(outputStream.toByteArray()));
        assertEquals("HTTP/1.1", tokenizer.nextToken());
        assertEquals("404", tokenizer.nextToken());
    }

    @Test
    public void request_otherResource_notFound() throws IOException {
        // setup
        prepareIncomingRequestStream(generateIncomingRequest("GET", "/favicon.ico"));

        // test
        requestHandler.run();

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
        requestHandler.run();

        //verify
        StringTokenizer tokenizer = new StringTokenizer(new String(outputStream.toByteArray()));
        assertEquals("HTTP/1.1", tokenizer.nextToken());
        assertEquals("501", tokenizer.nextToken());
    }

    @Test
    public void request_relative_bad_request() throws IOException {
        // setup the request
        prepareIncomingRequestStream(generateIncomingRequest("GET", "../css/main.css"));

        // test
        requestHandler.run();

        // verify
        StringTokenizer tokenizer = new StringTokenizer(new String(outputStream.toByteArray()));
        assertEquals("HTTP/1.1", tokenizer.nextToken());
        assertEquals("400", tokenizer.nextToken());
    }

    @Test
    public void request_gzip_success() throws IOException {
        // setup
        prepareIncomingRequestStream(generateGzipIncomingRequest());

        // test
        requestHandler.run();

        //verify
        StringTokenizer tokenizer = new StringTokenizer(new String(outputStream.toByteArray()));
        assertEquals("HTTP/1.1", tokenizer.nextToken());
        assertEquals("200", tokenizer.nextToken());
    }
    private String generateIncomingRequest(String method, String resource) {
        return method + " " + resource + " HTTP/1.1\n" +
                "Host: localhost:8080\n" +
                "User-Agent: curl/7.61.1\n" +
                "Accept: */*\n";
    }
    private String generateGzipIncomingRequest() {
        return "GET / HTTP/1.1\n" +
                "Host: localhost:8080\n" +
                "User-Agent: curl/7.61.1\n" +
                "Accept: */*\n" +
                "Accept-Encoding: gzip,deflate,br\n";
    }

    private void prepareIncomingRequestStream(String stream) throws IOException {
        InputStream inputStream = new ByteArrayInputStream(stream.getBytes());
        when(client.getInputStream()).thenReturn(inputStream);
    }
}
