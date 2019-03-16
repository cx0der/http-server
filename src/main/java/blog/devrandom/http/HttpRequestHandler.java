package blog.devrandom.http;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import static blog.devrandom.http.Http.Header.CONNECTION;
import static blog.devrandom.http.Http.Header.CONTENT_LENGTH;
import static blog.devrandom.http.Http.Header.CONTENT_TYPE;
import static blog.devrandom.http.Http.Header.DATE;
import static blog.devrandom.http.Http.Header.SERVER;
import static blog.devrandom.http.Http.Header.UA;

/**
 * {@link HttpRequestHandler} handles a single HTTP request per instance.
 */
public class HttpRequestHandler implements Runnable {

    static final String ROOT_PARAM = "server.root";
    private static final String SERVER_VERSION_PARAM = "server.response.version";

    private static final String INDEX_HTML = "index.html";
    private static final String NOT_FOUND = "404.html";
    private static final String NOT_IMPLEMENTED = "501.html";
    private static final DateTimeFormatter HTTP_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z");

    private final Socket client;
    private final Properties config;
    private final File serverRoot;
    private final PrintStream logger;

    /**
     * Constructor
     *
     * @param client Incoming request
     * @param config Server configuration
     */
    HttpRequestHandler(Socket client, Properties config, PrintStream logger) {
        this.client = client;
        this.config = config;
        serverRoot = new File(config.getProperty(ROOT_PARAM));
        this.logger = logger;
    }

    /**
     * This is the core method of the class which processes the incoming request.
     */
    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(client.getOutputStream());
             BufferedOutputStream dataOut = new BufferedOutputStream(client.getOutputStream())) {
            // read the first header.
            String header = in.readLine();
            StringTokenizer tokenizer = new StringTokenizer(header);
            String method = tokenizer.nextToken().toUpperCase();
            String resource = tokenizer.nextToken().toLowerCase();
            String protocol = tokenizer.nextToken();

            Map<String, String> requestHeaders = parseRequestHeaders(in);

            String status;
            String outputFileName;

            if (Http.Method.GET.equals(method)) {
                if (resource.endsWith("/")) {
                    outputFileName = INDEX_HTML;
                    status = Http.Status.OK;
                } else {
                    outputFileName = NOT_FOUND;
                    status = Http.Status.NOT_FOUND;
                }
            } else {
                outputFileName = NOT_IMPLEMENTED;
                status = Http.Status.NOT_IMPLEMENTED;
            }

            File file = new File(this.serverRoot, outputFileName);
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("GMT"));
            String date = now.format(HTTP_FORMATTER);

            byte[] data = readFile(file);

            // write the headers
            writeResponseHeaders(out, protocol, status, date, data.length);

            // write the file contents
            dataOut.write(data, 0, data.length);
            dataOut.flush();

            // log the request
            log(client.getInetAddress(), date, method, status, requestHeaders.getOrDefault(UA, ""));
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Helper method to write the HTTP response headers
     *
     * @param out      Stream to be written
     * @param protocol Response protocol
     * @param status   HTTP status code
     * @param date     HTTP formatted date
     * @param length   Content length
     */
    private void writeResponseHeaders(PrintWriter out, String protocol, String status, String date, int length) {
        out.println(protocol + status);
        out.println(SERVER + config.getProperty(SERVER_VERSION_PARAM));
        out.println(DATE + date);
        out.println(CONTENT_TYPE + "text/html; charset=utf-8");
        out.println(CONTENT_LENGTH + length);
        out.println(CONNECTION + "close");
        out.println();
        out.flush();
    }

    /**
     * Parse request to extract headers
     *
     * @param in Incoming request
     * @return {@link Map} of header name and value
     * @throws IOException When reading request
     */
    private Map<String, String> parseRequestHeaders(BufferedReader in) throws IOException {
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            int idx = line.indexOf(':');
            if (idx > 0) {
                headers.put(line.substring(0, idx).toLowerCase(), line.substring(idx + 1).trim());
            }
        }
        return headers;
    }

    /**
     * Helper method to read the response file
     *
     * @param file Response file to read
     * @return File contents
     * @throws IOException When a read exception occurs
     */
    private byte[] readFile(File file) throws IOException {
        byte[] res;
        try (FileInputStream fis = new FileInputStream(file)) {
            int length = (int) file.length();
            res = new byte[length];
            //noinspection ResultOfMethodCallIgnored
            fis.read(res, 0, length);
        }
        return res;
    }

    /**
     * Helper method to log in certain manner
     *
     * @param remoteAddress {@link InetAddress} of the incoming request
     * @param date       Formatted date string when the request was serviced
     * @param request    Requested resource
     * @param status     Http Response Code
     * @param ua         User Agent String
     */
    private void log(InetAddress remoteAddress, String date, String request, String status, String ua) {
        logger.printf("%s [%s] \"%s\"%s %s\n", remoteAddress.getHostAddress(), date, request, status, ua);
    }
}
