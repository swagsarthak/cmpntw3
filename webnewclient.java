import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.security.NoSuchAlgorithmException;
import java.security.KeyManagementException;

public class webnewclient {

    private static SSLSocketFactory createSSLSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
        };

        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        return sc.getSocketFactory();
    }

    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.out.println("java webclient <url> [-f filename] [-nf] [-ping] [-pkt] [-info]");
                System.exit(1);
            }

            String url1 = args[0];
            if (!url1.startsWith("http://") && !url1.startsWith("https://")) {
                url1 = "http://" + url1;
            }

            URL url = new URL(url1);
            String host = url.getHost();
            String path = url.getPath().isEmpty() ? "/" : url.getPath();
            int port = url.getPort();
            boolean isHttps = url1.startsWith("https://");
            
            if (port == -1) {
                port = isHttps ? 443 : 80;
            }

            String fileName = "webout";
            boolean savio = true;
            boolean isPing = false;
            boolean pktio = false;
            boolean showInfo = false;

            if (args.length >= 2) {
                for (int i = 1; i < args.length; i++) {
                    switch (args[i]) {
                        case "-f":
                            if (i + 1 < args.length) {
                                fileName = args[++i];
                            }
                            break;
                        case "-nf":
                            savio = false;
                            break;
                        case "-ping":
                            isPing = true;
                            break;
                        case "-pkt":
                            pktio = true;
                            savio = false;
                            break;
                        case "-info":
                            showInfo = true;
                            break;
                    }
                }
            }

            if (isPing) {
                ping(host);
                return;
            }

            Socket socket;
            if (isHttps) {
                SSLSocketFactory sslSocketFactory = createSSLSocketFactory();
                SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(host, port);
                
                String[] protocols = sslSocket.getSupportedProtocols();
                sslSocket.setEnabledProtocols(protocols);

                String[] cipherSuites = sslSocket.getSupportedCipherSuites();
                sslSocket.setEnabledCipherSuites(cipherSuites);

                sslSocket.startHandshake();
                
                socket = sslSocket;
            } else {
                socket = new Socket(host, port);
            }

            if (showInfo) {
                con_info(socket);
            }

            OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream());
            writer.write("GET " + path + " HTTP/1.1\r\n");
            writer.write("Host: " + host + "\r\n");
            writer.write("Connection: close\r\n");
            writer.write("\r\n");
            writer.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;

            if (pktio) {
                List<String> packetData = new ArrayList<>();
                long startTime = System.currentTimeMillis();
                int totalBytes = 0;

                while ((line = reader.readLine()) != null) {
                    long currentTime = System.currentTimeMillis() - startTime;
                    int bytesRead = line.getBytes().length;
                    totalBytes += bytesRead;
                    packetData.add("Time: " + currentTime + " ms, Bytes " + bytesRead);
                }

                System.out.println("Packet Collected");
                for (String pkt : packetData) {
                    System.out.println(pkt);
                }
                System.out.println("Total Bytes Received " + totalBytes);

            } else {
                if (savio) {
                    BufferedWriter fileWriter = new BufferedWriter(new FileWriter(fileName));
                    while ((line = reader.readLine()) != null) {
                        fileWriter.write(line);
                        fileWriter.newLine();
                    }
                    fileWriter.close();
                    System.out.println("Response saved to " + fileName);
                } else {
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            }

            reader.close();
            writer.close();
            socket.close();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void ping(String host) {
        try {
            InetAddress inet = InetAddress.getByName(host);
            long startTime = System.currentTimeMillis();

            if (inet.isReachable(5000)) {
                long endTime = System.currentTimeMillis();
                long rtt = endTime - startTime;
                System.out.println(inet.getHostAddress() + " RTT: " + rtt + " ms");
            } else {
                System.out.println(host + " is not reachable.");
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private static void con_info(Socket socket) {
        try {
            System.out.println("Local Address: " + socket.getLocalAddress());
            InetAddress inet = socket.getInetAddress();
            System.out.println("Remote Address: " + inet.getHostAddress());
            System.out.println("Remote Port: " + socket.getPort());
            System.out.println("Connection Type: " + (socket instanceof SSLSocket ? "HTTPS" : "HTTP"));
            
            if (socket instanceof SSLSocket) {
                SSLSocket sslSocket = (SSLSocket) socket;
                SSLSession session = sslSocket.getSession();
                System.out.println("SSL Protocol: " + session.getProtocol());
                System.out.println("Cipher Suite: " + session.getCipherSuite());
                System.out.println("Enabled Protocols: " + String.join(", ", sslSocket.getEnabledProtocols()));
            }

            String host = inet.getHostAddress();
            Process process = Runtime.getRuntime().exec("ping -c 1 " + host);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("time=")) {
                    String[] parts = line.split(" ");
                    for (String part : parts) {
                        if (part.startsWith("time=")) {
                            String rtt = part.split("=")[1];
                            System.out.println("RTT: " + rtt + " ms");
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }
}