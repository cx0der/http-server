package blog.devrandom.http;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.zip.GZIPOutputStream;

import static blog.devrandom.http.Http.Header.CONNECTION;
import static blog.devrandom.http.Http.Header.CONTENT_ENCODING;
import static blog.devrandom.http.Http.Header.CONTENT_LENGTH;
import static blog.devrandom.http.Http.Header.CONTENT_TYPE;
import static blog.devrandom.http.Http.Header.DATE;
import static blog.devrandom.http.Http.Header.METHOD;
import static blog.devrandom.http.Http.Header.PROTOCOL;
import static blog.devrandom.http.Http.Header.RESOURCE;
import static blog.devrandom.http.Http.Header.SERVER;
import static blog.devrandom.http.Http.Header.UA;

/**
 * {@link HttpRequestHandler} handles a single HTTP request per instance.
 */
public class HttpRequestHandler implements Runnable {

    static final String ROOT_PARAM = "server.root";
    private static final String WEB_ROOT = "web.root";
    private static final String SERVER_VERSION_PARAM = "server.response.version";

    private static final String INDEX_HTML = "index.html";
    private static final String BAD_REQUEST = "400.html";
    private static final String NOT_FOUND = "404.html";
    private static final String NOT_IMPLEMENTED = "501.html";
    private static final String GZIP = "gzip";
    private static final DateTimeFormatter HTTP_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z");

    private final Socket client;
    private final Properties config;
    private final File serverRoot;
    private final String webRoot;
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
        webRoot = config.getProperty(WEB_ROOT);
        this.logger = logger;
    }

    /**
     * This is the core method of the class which processes the incoming request.
     */
    @Override
    public void run() {

        OutputStream dataOut = null;

        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(client.getOutputStream())) {

            Map<String, String> requestHeaders = parseRequestHeaders(in);

            String method = requestHeaders.get(METHOD);
            String resource = requestHeaders.get(RESOURCE);
            String protocol = requestHeaders.get(PROTOCOL);

            String status;
            String resolvedResourcePath;
            File outputFile;

            if (resource.contains("./") || resource.contains("../")) {
                status = Http.Status.BAD_REQUEST;
                outputFile = new File(this.serverRoot, BAD_REQUEST);
            } else if (Http.Method.GET.equals(method)) {
                resolvedResourcePath = resolveResource(resource);
                outputFile = new File(this.webRoot, resolvedResourcePath);

                if (!outputFile.exists()) {
                    status = Http.Status.NOT_FOUND;
                    outputFile = new File(this.serverRoot, NOT_FOUND);
                } else {
                    if (outputFile.isDirectory()) {
                        outputFile = new File(outputFile, INDEX_HTML);
                    }
                    if (outputFile.exists()) {
                        status = Http.Status.OK;
                    } else {
                        status = Http.Status.NOT_FOUND;
                        outputFile = new File(this.serverRoot, NOT_FOUND);
                    }
                }
            } else {
                outputFile = new File(this.serverRoot, NOT_IMPLEMENTED);
                status = Http.Status.NOT_IMPLEMENTED;
            }

            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("GMT"));
            String date = now.format(HTTP_FORMATTER);

            String mimeType = Files.probeContentType(outputFile.toPath());
            byte[] data = readFile(outputFile);

            // write the headers
            writeResponseHeaders(out, protocol, status, mimeType, date, data.length, getContentEncoding(requestHeaders));

            // write the outputFile contents
            dataOut = getDataOutputStream(requestHeaders, client.getOutputStream());
            dataOut.write(data, 0, data.length);
            if (dataOut instanceof GZIPOutputStream) {
                ((GZIPOutputStream) dataOut).finish();
            } else {
                dataOut.flush();
            }

            // log the request
            log(client.getInetAddress(), date, method, status, requestHeaders.getOrDefault(UA, ""), resource);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } finally {
            if (dataOut != null) {
                try {
                    dataOut.close();
                } catch (IOException ex) {
                    System.out.println(ex.getMessage());
                }
            }
        }
    }

    /**
     * Helper method to write the HTTP response headers
     *
     * @param out             Stream to be written
     * @param protocol        Response protocol
     * @param status          HTTP status code
     * @param mimeType        MIME type
     * @param date            HTTP formatted date
     * @param length          Content length
     * @param contentEncoding content encoding format
     */
    private void writeResponseHeaders(PrintWriter out, String protocol, String status, String mimeType, String date,
                                      int length, String contentEncoding) {
        out.println(protocol + status);
        out.println(SERVER + config.getProperty(SERVER_VERSION_PARAM));
        out.println(DATE + date);
        if (contentEncoding != null)
            out.println(CONTENT_ENCODING + contentEncoding);
        out.println(CONTENT_TYPE + mimeType + ";charset=\"utf-8\"");
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
        // parse the first line.
        String header = in.readLine();
        StringTokenizer tokenizer = new StringTokenizer(header);
        headers.put(METHOD, tokenizer.nextToken().toUpperCase());
        headers.put(RESOURCE, tokenizer.nextToken().toLowerCase());
        headers.put(PROTOCOL, tokenizer.nextToken());
        // Rest of the headers
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
     * @param date          Formatted date string when the request was serviced
     * @param request       Requested resource
     * @param status        Http Response Code
     * @param ua            User Agent String
     */
    private void log(InetAddress remoteAddress, String date, String request, String status, String ua,
                     String resource) {
        logger.printf("%s [%s] \"%s\"%s %s %s\n", remoteAddress.getHostAddress(), date, request, status, ua, resource);
    }

    /**
     * Helper method to sanitize and resolve the requested resource
     *
     * @param requestedPath resource requested
     * @return resolved path
     */
    private String resolveResource(String requestedPath) {
        Path resolvedPath = FileSystems.getDefault().getPath("");
        Path other = FileSystems.getDefault().getPath(requestedPath);
        for (Path path : other) {
            if (!path.startsWith(".") && !path.startsWith("..")) {
                resolvedPath = resolvedPath.resolve(path);
            }
        }
        if (resolvedPath.startsWith("")) {
            resolvedPath = resolvedPath.resolve(INDEX_HTML);
        }
        return resolvedPath.toString();
    }

    /**
     * Helper method to get the correct type of output stream
     *
     * @param requestHeaders request headers
     * @param outputStream   base output stream
     * @return {@link GZIPOutputStream} or {@link BufferedOutputStream} depending on the request header
     * @throws IOException When error occurs while creating {@link GZIPOutputStream}
     */
    private OutputStream getDataOutputStream(Map<String, String> requestHeaders, OutputStream outputStream)
            throws IOException {
        String acceptedEncoding = requestHeaders.getOrDefault(Http.Header.ACCEPT_ENCODING, "");
        if (acceptedEncoding.contains("gzip")) {
            return new GZIPOutputStream(outputStream);
        }
        return new BufferedOutputStream(outputStream);
    }

    private String getContentEncoding(Map<String, String> requestHeaders) {
        String acceptedEncoding = requestHeaders.getOrDefault(Http.Header.ACCEPT_ENCODING, "");
        return acceptedEncoding.contains(GZIP) ? GZIP : null;
    }
}
