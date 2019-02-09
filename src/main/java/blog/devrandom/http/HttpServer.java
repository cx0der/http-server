package blog.devrandom.http;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.StringTokenizer;

public class HttpServer implements Runnable {

    private static final File WEB_ROOT = new File(".");
    private static final File INDEX_HTML = new File(WEB_ROOT, "index.html");
    private static final File NOT_FOUND = new File(WEB_ROOT, "404.html");
    private static final File NOT_IMPLEMENTED = new File(WEB_ROOT, "501.html");
    private static final DateTimeFormatter HTTP_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z");

    private Socket request;

    private HttpServer(Socket request) {
        this.request = request;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(request.getInputStream()));
             PrintWriter out = new PrintWriter(request.getOutputStream());
             BufferedOutputStream dataOut = new BufferedOutputStream(request.getOutputStream())) {
            // read the first header.
            String header = in.readLine();
            StringTokenizer tokenizer = new StringTokenizer(header);
            String method = tokenizer.nextToken().toUpperCase();
            String resource = tokenizer.nextToken().toLowerCase();
            String protocol = tokenizer.nextToken();

            String status;
            File file;

            if (method.equals("GET")) {
                if (resource.endsWith("/")) {
                    file = INDEX_HTML;
                    status = " 200 OK";
                } else {
                    file = NOT_FOUND;
                    status = " 404 Not Found";
                }
            } else {
                file = NOT_IMPLEMENTED;
                status = " 501 Not Implemented";
            }

            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("GMT"));
            String date = now.format(HTTP_FORMATTER);

            System.out.printf("%s %s%s %s\n", method, resource, status, date);
            byte[] data = readFile(file);

            // write the headers
            out.println(protocol + status);
            out.println("Server: HttpServer v1.0");
            out.println("Date: " + date);
            out.println("Content-Type: text/html; charset=utf-8");
            out.println("Content-Length: " + data.length);
            out.println();
            out.flush();

            // write the file contents
            dataOut.write(data, 0, data.length);
            dataOut.flush();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    private byte[] readFile(File file) throws IOException {
        byte[] res;
        try (FileInputStream fis = new FileInputStream(file)) {
            int length = (int) file.length();
            res = new byte[length];
            fis.read(res, 0, length);
        }
        return res;
    }

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(8080)) {
            System.out.println("HttpServer started and listening to port 8080");
            // infinite loop
            while (true) {
                HttpServer server = new HttpServer(serverSocket.accept());

                Thread thread = new Thread(server);
                thread.start();
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}
